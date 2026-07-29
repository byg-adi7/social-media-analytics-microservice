#!/usr/bin/env bash
# Automated cross-service round-trip smoke test, run against a live stack
# (docker compose up) through the public gateway. Exercises the exact flow
# that was previously only ever verified by hand: connect an account -> the
# resulting notification -> generate a report -> list reports. Exits
# non-zero on the first failed assertion.
#
# Identity is now fully delegated to Supabase Auth (the frontend talks to
# it directly) - this script has no real Supabase account to log in with in
# CI, so it generates its own Supabase-shaped JWT signed with the same
# SUPABASE_JWT_SECRET the stack was started with. That's exactly what Auth
# Service's /validate endpoint checks; it has no way to tell this apart
# from a token Supabase actually issued.
#
# Usage: BASE_URL=http://localhost:8080 SUPABASE_JWT_SECRET=... ./scripts/smoke-test.sh
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
: "${SUPABASE_JWT_SECRET:?Set SUPABASE_JWT_SECRET (must match what the stack was started with)}"
FAILURES=0

assert_eq() {
    local description="$1" expected="$2" actual="$3"
    if [ "$expected" = "$actual" ]; then
        echo "  OK   $description"
    else
        echo "  FAIL $description (expected [$expected], got [$actual])"
        FAILURES=$((FAILURES + 1))
    fi
}

assert_contains() {
    local description="$1" haystack="$2" needle="$3"
    if echo "$haystack" | grep -q "$needle"; then
        echo "  OK   $description"
    else
        echo "  FAIL $description (expected to find [$needle] in: $haystack)"
        FAILURES=$((FAILURES + 1))
    fi
}

b64url() {
    openssl base64 -e -A | tr '+/' '-_' | tr -d '='
}

make_supabase_token() {
    local user_id="$1" email="$2" secret="${3:-$SUPABASE_JWT_SECRET}"
    local header='{"alg":"HS256","typ":"JWT"}'
    local iat exp payload header_b64 payload_b64 signing_input signature
    iat=$(date +%s)
    exp=$((iat + 3600))
    payload="{\"sub\":\"$user_id\",\"email\":\"$email\",\"role\":\"authenticated\",\"iat\":$iat,\"exp\":$exp}"
    header_b64=$(printf '%s' "$header" | b64url)
    payload_b64=$(printf '%s' "$payload" | b64url)
    signing_input="${header_b64}.${payload_b64}"
    signature=$(printf '%s' "$signing_input" | openssl dgst -sha256 -hmac "$secret" -binary | b64url)
    echo "${signing_input}.${signature}"
}

echo "=== Smoke test against $BASE_URL ==="
RUN_ID="$(date +%s)-$RANDOM"
EMAIL="smoketest-$RUN_ID@example.com"
USER_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
# Unique per run, not just the email: uk_platform_account_id is a GLOBAL
# constraint (platform + account_id, not scoped per user - see the fix for
# this exact class of bug), so a fixed accountId would spuriously fail on
# a second local run against a database that wasn't wiped between runs.
# CI always starts from a fresh volume, so this only matters for repeated
# local runs.
ACCOUNT_ID="yt-smoketest-$RUN_ID"

echo "--- generate a Supabase-shaped test token and confirm Auth Service validates it ---"
TOKEN=$(make_supabase_token "$USER_ID" "$EMAIL")
VALIDATE_RESPONSE=$(curl -s "$BASE_URL/api/auth/validate" -H "Authorization: Bearer $TOKEN")
assert_contains "validate reports valid:true" "$VALIDATE_RESPONSE" "\"valid\":true"
assert_contains "validate echoes back the test user id" "$VALIDATE_RESPONSE" "$USER_ID"

echo "--- connect a mock YouTube account ---"
CONNECT_BODY=$(mktemp)
CONNECT_STATUS=$(curl -s -o "$CONNECT_BODY" -w "%{http_code}" -X POST "$BASE_URL/api/accounts/connect" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"platform\":\"YOUTUBE\",\"accountId\":\"$ACCOUNT_ID\",\"accountName\":\"Smoke Test Channel\",\"accessToken\":\"mock-token\"}")
CONNECT_RESPONSE=$(cat "$CONNECT_BODY"); rm -f "$CONNECT_BODY"
assert_eq "connect returns 201" "201" "$CONNECT_STATUS"
assert_contains "connected account is YOUTUBE" "$CONNECT_RESPONSE" "YOUTUBE"

echo "--- unauthenticated request is rejected ---"
NOAUTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/accounts")
assert_eq "no-token request returns 401" "401" "$NOAUTH_STATUS"

echo "--- account-connected notification fired automatically ---"
NOTIF_RESPONSE=$(curl -s "$BASE_URL/api/notifications" -H "Authorization: Bearer $TOKEN")
assert_contains "notification list contains ACCOUNT_CONNECTED" "$NOTIF_RESPONSE" "ACCOUNT_CONNECTED"

echo "--- generate a platform-comparison report (Notification Service -> Analytics Service) ---"
TODAY=$(date -u +%Y-%m-%d)
REPORT_BODY=$(mktemp)
REPORT_STATUS=$(curl -s -o "$REPORT_BODY" -w "%{http_code}" -X POST "$BASE_URL/api/reports" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"reportType\":\"PLATFORM_COMPARISON\",\"startPeriod\":\"$TODAY\",\"endPeriod\":\"$TODAY\"}")
REPORT_RESPONSE=$(cat "$REPORT_BODY"); rm -f "$REPORT_BODY"
assert_eq "report generation returns 201" "201" "$REPORT_STATUS"
assert_contains "report status is COMPLETED" "$REPORT_RESPONSE" "COMPLETED"
assert_contains "report content has a CSV header row" "$REPORT_RESPONSE" "platform,followers"

echo "--- report-ready notification fired ---"
NOTIF_RESPONSE_2=$(curl -s "$BASE_URL/api/notifications" -H "Authorization: Bearer $TOKEN")
assert_contains "notification list contains REPORT_READY" "$NOTIF_RESPONSE_2" "REPORT_READY"

echo "--- list reports ---"
LIST_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/reports" -H "Authorization: Bearer $TOKEN")
assert_eq "list reports returns 200" "200" "$LIST_STATUS"

echo "--- internal notification-service endpoint is not reachable through the public gateway ---"
INTERNAL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/internal/notifications" \
    -H "X-Internal-Api-Key: whatever" -H "Content-Type: application/json" \
    -d '{"userId":"00000000-0000-0000-0000-000000000000","type":"ACCOUNT_CONNECTED","message":"should not work"}')
assert_eq "internal endpoint returns 404 via gateway" "404" "$INTERNAL_STATUS"

echo "--- a token signed with the wrong secret is rejected ---"
FORGED_TOKEN=$(make_supabase_token "$USER_ID" "$EMAIL" "a-completely-different-secret")
FORGED_RESPONSE=$(curl -s "$BASE_URL/api/auth/validate" -H "Authorization: Bearer $FORGED_TOKEN")
assert_contains "forged token reports valid:false" "$FORGED_RESPONSE" "\"valid\":false"

echo ""
if [ "$FAILURES" -eq 0 ]; then
    echo "=== All smoke test assertions passed ==="
    exit 0
else
    echo "=== $FAILURES smoke test assertion(s) FAILED ==="
    exit 1
fi
