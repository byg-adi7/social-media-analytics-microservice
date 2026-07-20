#!/usr/bin/env bash
# Automated cross-service round-trip smoke test, run against a live stack
# (docker compose up) through the public gateway. Exercises the exact flow
# that was previously only ever verified by hand: register -> login ->
# connect an account -> the resulting notification -> generate a report ->
# list reports. Exits non-zero on the first failed assertion.
#
# Usage: BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
FAILURES=0

extract() {
    # extract <json> <field-name> - naive but sufficient for this flat,
    # controlled response shape (no nested objects sharing a field name).
    echo "$1" | sed -n "s/.*\"$2\":\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" | head -1
}

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

echo "=== Smoke test against $BASE_URL ==="
EMAIL="smoketest-$(date +%s)-$RANDOM@example.com"

echo "--- register ---"
REGISTER_BODY=$(mktemp)
REGISTER_STATUS=$(curl -s -o "$REGISTER_BODY" -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"smoketest\",\"email\":\"$EMAIL\",\"password\":\"Password123!\"}")
REGISTER_RESPONSE=$(cat "$REGISTER_BODY"); rm -f "$REGISTER_BODY"
assert_eq "register returns 200" "200" "$REGISTER_STATUS"
TOKEN=$(extract "$REGISTER_RESPONSE" "token")
if [ -z "$TOKEN" ]; then
    echo "  FAIL no token in register response, cannot continue: $REGISTER_RESPONSE"
    exit 1
fi
echo "  OK   received a token"

echo "--- connect a mock YouTube account ---"
CONNECT_BODY=$(mktemp)
CONNECT_STATUS=$(curl -s -o "$CONNECT_BODY" -w "%{http_code}" -X POST "$BASE_URL/api/accounts/connect" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d '{"platform":"YOUTUBE","accountId":"yt-smoketest","accountName":"Smoke Test Channel","accessToken":"mock-token"}')
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

echo "--- wrong password returns a clean error, not a stack trace ---"
WRONGPW_BODY=$(mktemp)
WRONGPW_STATUS=$(curl -s -o "$WRONGPW_BODY" -w "%{http_code}" -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"WrongPassword!\"}")
WRONGPW_RESPONSE=$(cat "$WRONGPW_BODY"); rm -f "$WRONGPW_BODY"
assert_eq "wrong password returns 401" "401" "$WRONGPW_STATUS"
assert_contains "wrong password error body is clean JSON" "$WRONGPW_RESPONSE" "Invalid email or password"

echo ""
if [ "$FAILURES" -eq 0 ]; then
    echo "=== All smoke test assertions passed ==="
    exit 0
else
    echo "=== $FAILURES smoke test assertion(s) FAILED ==="
    exit 1
fi
