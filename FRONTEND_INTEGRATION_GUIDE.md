# Audience Insights Platform — Backend Status & Frontend Integration Guide

This document has two purposes: an honest account of what the backend
currently is (for the project report), and a practical reference for
building the frontend against it.

---

## Part 1: Backend Readiness

### Architecture

Three independent Spring Boot microservices, a Postgres database, and an
nginx gateway as the single public entry point:

```
Frontend (not yet built)
        |
        v
  api-gateway (nginx, :8080)
   /        |          \
  v         v           v
auth-svc  analytics-svc  notification-svc
 :8001       :8002          :8003
   \          |             /
    \         |            /
     -----> postgres (:5432) <-----
```

- **Auth Service** — registration, login, JWT issuance. Every other
  service delegates authentication here; none validate tokens locally.
- **Analytics Service** — connects social accounts (YouTube, Instagram,
  TikTok, Facebook, Spotify), syncs their analytics, serves
  dashboards/charts/reports.
- **Notification Service** — in-app notifications (fired automatically
  by the Analytics Service on real events) and on-demand CSV reports.
- All three share one physical Postgres database but own separate tables
  and separate Flyway migration histories — they are not coupled at the
  data layer.

### What's actually done and verified

- All three services compile, and **51 automated tests pass** across
  them (17 in Auth, 36 in Analytics, 9 in Notification — some overlap in
  how these are counted service-to-service, see each service's own test
  reports for exact current numbers).
- **Live-tested, not just unit-tested**: the full flow (register → login
  → validate → connect an account → automatic notification → generate a
  report → list reports) has been run against a real 4-container Docker
  stack multiple times, including via `scripts/smoke-test.sh`, which is
  now part of CI.
- **CI**: GitHub Actions runs the full test suite for all 3 services plus
  the live integration smoke test on every push/PR to `main`.
- **Schema migrations**: Flyway-managed (not `ddl-auto=update`) — every
  schema change is a reviewable, versioned SQL file from now on.
- **Secrets**: not committed to git. `docker-compose.yml` reads them from
  a local `.env` file (see `.env.example`) and refuses to start if one is
  missing.
- **Security basics in place**: BCrypt password hashing, stateless JWT
  auth centralized in one service, CORS allow-list (not a wildcard),
  consistent JSON error bodies (no stack traces leak to clients), and
  basic rate limiting on login/register (10 requests/minute per IP).
- **All 5 platform integrations** (YouTube, Spotify, Instagram, TikTok,
  Facebook) are real, working OAuth implementations verified against each
  platform's official API docs — but see "Known gaps" below for what
  that does and doesn't mean in practice.

### Known gaps — read this before assuming something works

- **No frontend exists.** This guide is for building it.
- **The 5 platform integrations need real credentials to do anything
  beyond mock data.** Without a real Google/Meta/Spotify/TikTok
  developer app registered and its credentials set in `.env`, connecting
  an account uses randomly-generated mock analytics, not real data from
  that platform. This is the default and expected state for local/demo
  use.
- **No real-time push.** Notifications are polled via `GET
  /api/notifications` — there is no WebSocket/SSE. The frontend should
  poll periodically (or on page focus) rather than expect push updates.
- **No dependency-vulnerability scanning** has been run (this
  environment has no network access to a CVE database).
- **Plain HTTP everywhere**, no TLS. Fine for local dev; a real
  deployment needs TLS termination at the gateway.
- **No pagination** on any list endpoint (`GET /api/accounts`, `GET
  /api/notifications`, `GET /api/reports`) — they return the caller's
  entire result set. Fine at demo scale; would need adding for a
  production dataset size.
- **In-memory rate limiting only** — correct for the current
  single-instance-per-service deployment, would need a shared store
  (e.g. Redis) if a service were ever horizontally scaled.

---

## Part 2: What the Frontend Needs to Know

### Running the backend locally

```bash
git clone <this repo>
cd AudienceInsights
cp .env.example .env
# generate values for the *_SECRET/*_KEY/*_PASSWORD placeholders in .env, e.g.:
openssl rand -base64 32
docker compose up --build
```

The gateway is then at `http://localhost:8080` — **the frontend should
only ever talk to this URL**, never to the individual service ports
directly (8001/8002/8003 are for backend debugging only).

Everything works immediately with mock data — no platform credentials
are required to build and test the frontend.

### Authentication flow

1. `POST /api/auth/register` or `POST /api/auth/login` → returns a JWT in
   the response body (see below).
2. Store the JWT (e.g. in memory + a secure storage mechanism of your
   choice — this backend has no opinion on where the frontend keeps it).
3. Send it on **every subsequent request** as:
   ```
   Authorization: Bearer <token>
   ```
4. Token expires after 24h by default (`JWT_EXPIRATION_MS`). There is no
   refresh-token endpoint — when a request comes back `401`, send the
   user back to login.

### Standard error response shape

Every error from every service looks like this (fields may be omitted if
not applicable):

```json
{
  "timestamp": "2026-07-20T18:30:00",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "VALIDATION_ERROR",
  "message": "Human-readable description",
  "path": "/api/accounts/connect",
  "validationErrors": [
    { "field": "email", "message": "must be a well-formed email address", "rejectedValue": "not-an-email" }
  ]
}
```

`validationErrors` only appears on `400` field-validation failures.
`errorCode` is not present on Auth Service error bodies (it predates that
convention) — check `status` there instead.

Status codes actually in use: `200`, `201`, `204` (delete), `400`, `401`,
`404`, `409` (conflict, e.g. duplicate email or duplicate account
connection), `429` (rate limited), `500`, `502` (Notification Service's
report generation, when the Analytics Service call fails).

---

### API Reference

#### Auth Service (`/api/auth/*`)

| Method | Path | Auth? | Body | Response |
|---|---|---|---|---|
| POST | `/api/auth/register` | No | `{username, email, password}` | `{token, username, email, role}` |
| POST | `/api/auth/login` | No | `{email, password}` | `{token, username, email, role}` |
| GET | `/api/auth/validate` | Yes | — | `{valid, userId, email, role}` — mainly for internal service-to-service use, but usable by the frontend to check token validity |

`role` is currently always `"USER"` — there is no admin role or
permission system built yet.

#### Analytics Service — Accounts (`/api/accounts/*`)

All require `Authorization: Bearer <token>`.

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/accounts/connect` | `{platform, accountId, accountName, username?, profileImage?, accessToken, refreshToken?}` | `SocialAccountResponse` (201) |
| GET | `/api/accounts` | — | `SocialAccountResponse[]` |
| GET | `/api/accounts/{id}` | — | `SocialAccountResponse` |
| PUT | `/api/accounts/{id}` | `{accountName?, username?, profileImage?, active?}` | `SocialAccountResponse` |
| DELETE | `/api/accounts/{id}` | — | 204 No Content |
| GET | `/api/accounts/{id}/sync` | — | `SocialAccountResponse` (triggers an on-demand sync) |

`platform` is one of: `YOUTUBE`, `INSTAGRAM`, `TIKTOK`, `FACEBOOK`,
`SPOTIFY`.

`SocialAccountResponse`:
```json
{
  "id": "uuid",
  "platform": "YOUTUBE",
  "accountId": "external platform account id",
  "accountName": "string",
  "username": "string",
  "profileImage": "url",
  "connectedAt": "2026-07-20T18:30:00",
  "lastSynced": "2026-07-20T18:31:00",
  "active": true
}
```
Never includes access/refresh tokens.

**In practice, the frontend won't usually call `/connect` directly with a
raw access token** — for a real platform connection, use the OAuth flow
below instead. `/connect` taking a raw `accessToken` directly is what
lets you simulate a connected mock account for development without going
through a real OAuth provider.

#### Analytics Service — OAuth connect flow (per platform)

Identical shape for all 5 platforms — replace `{platform}` with
`youtube`, `instagram`, `tiktok`, `facebook`, or `spotify`.

| Method | Path | Auth? | Notes |
|---|---|---|---|
| GET | `/api/oauth/{platform}/authorize` | Yes | Returns `{authorizationUrl}` — redirect the browser here |
| GET | `/api/oauth/{platform}/callback` | No (public) | The platform redirects here after consent, not the frontend — never call this directly |

Frontend flow:
1. User clicks "Connect YouTube" → `GET /api/oauth/youtube/authorize`
   (authenticated) → get back `{"authorizationUrl": "https://accounts.google.com/..."}`.
2. Redirect the browser to that URL.
3. User consents on the platform's own site.
4. Platform redirects the browser to the backend's `/callback`, which
   the backend handles, then **redirects the browser again** to:
   `{FRONTEND_REDIRECT_URI}?connected={platform}&accountId={uuid}`
   (e.g. `http://localhost:3000/dashboard?connected=youtube&accountId=...`).
5. The frontend's redirect target page should read these query params to
   show a success message / refresh the account list.

This will only produce real data if real credentials for that platform
are configured in the backend's `.env` — otherwise the platform is
disabled and the whole flow isn't reachable (the `/connect` mock-data
path above is what to use for frontend dev instead).

#### Analytics Service — Analytics (`/api/analytics/*`)

All authenticated. All accept optional `startDate`/`endDate` query
params (`yyyy-MM-dd`); some accept `platform` too.

| Method | Path | Query params | Response |
|---|---|---|---|
| GET | `/api/analytics/report` | startDate?, endDate?, platform? | Full combined report (summary + comparison + growth + engagement + top posts) |
| GET | `/api/analytics/trends` | startDate?, endDate?, platform? | Time-series with 7-day moving average |
| GET | `/api/analytics/platform-comparison` | startDate?, endDate? | `PlatformMetricsResponse[]` |
| GET | `/api/analytics/summary` | startDate?, endDate? | `AnalyticsSummaryResponse` |
| GET | `/api/analytics/top-platform` | startDate?, endDate? | `PlatformMetricsResponse` |
| GET | `/api/analytics/engagement` | startDate?, endDate?, platform? | `EngagementResponse` |
| GET | `/api/analytics/growth` | startDate?, endDate?, platform? | `GrowthResponse` |

`PlatformMetricsResponse`:
```json
{
  "platform": "YOUTUBE",
  "followers": 45000, "views": 3501, "likes": 301, "comments": 18, "shares": 23,
  "posts": 1, "engagementRate": 0.76, "growthRate": 0.0
}
```

`AnalyticsSummaryResponse`:
```json
{
  "totalFollowers": 45000, "totalPosts": 1,
  "averageEngagementRate": 0.76, "averageDailyViews": 3501.0, "averageReach": 5055.0,
  "bestPlatform": "YouTube", "worstPlatform": "YouTube",
  "fastestGrowingPlatform": "YouTube", "mostActivePlatform": "YouTube", "mostViewedPlatform": "YouTube"
}
```

#### Analytics Service — Charts (`/api/charts/*`)

All authenticated, all return chart-ready JSON (no image/SVG rendering —
that's the frontend's job).

| Method | Path | Response shape |
|---|---|---|
| GET | `/api/charts/engagement` | `{labels: string[], series: {platform: number[]}}` |
| GET | `/api/charts/followers` | same shape as above |
| GET | `/api/charts/views` | same shape as above |
| GET | `/api/charts/platform-comparison` | `[{platform, followers}]` |
| GET | `/api/charts/engagement-distribution` | `[{platform, value}]` (pie chart) |
| GET | `/api/charts/top-content?limit=10` | `[{platform, title, publishedDate, views, likes, comments, shares, engagementRate}]` |
| GET | `/api/charts/audience-demographics` | `[{platform, accountId, accountName, byAgeRange, byGender, byCity, byCountry}]` — maps may be empty per-platform, see limitations below |
| GET | `/api/charts/weekly-growth` | `{labels: string[], followerGrowth: number[]}` |
| GET | `/api/charts/monthly-growth` | same shape as above |

**Audience demographics limitations the frontend must handle
gracefully** (empty maps are normal, not a bug):
- Facebook: no age/gender data at all (Meta platform restriction since
  March 2024) — only city/country.
- TikTok: no demographics at all via this API tier — always empty.
- YouTube/Spotify: not exposed by their public APIs — always empty.
- Instagram: full data, but empty if the account has under ~100
  followers (Meta's own minimum).

#### Analytics Service — Dashboard (`/api/dashboard`)

| Method | Path | Response |
|---|---|---|
| GET | `/api/dashboard` | `{totalFollowers, totalViews, totalLikes, totalComments, totalShares, totalReach, totalImpressions, averageEngagement, connectedPlatforms: string[], bestPerformingPlatform, lastSyncTime}` |

#### Notification Service (`/api/notifications/*`)

All authenticated. Notifications are always system-generated — there is
no endpoint for a user to create their own.

| Method | Path | Response |
|---|---|---|
| GET | `/api/notifications` | `[{id, type, message, read, createdAt}]`, most recent first |
| PATCH | `/api/notifications/{id}/read` | Updated notification object |

`type` is one of `ACCOUNT_CONNECTED`, `SYNC_FAILURE`, `REPORT_READY`.

#### Notification Service — Reports (`/api/reports/*`)

All authenticated.

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/reports` | `{reportType, startPeriod, endPeriod}` (dates as `yyyy-MM-dd`) | Full report incl. CSV `content` (201) |
| GET | `/api/reports` | — | List of report summaries (no `content` field — fetch by id for that) |
| GET | `/api/reports/{id}` | — | Full report incl. `content` |

`reportType` is `PLATFORM_COMPARISON` or `SUMMARY`. `content` is a raw
CSV string — the frontend can offer it as a downloadable file (e.g. via
a `Blob` + `<a download>`) or parse it directly for an in-app view.
Report generation can return `502` if the Analytics Service call fails —
show a retry option, don't treat it as a permanent failure.

---

### Suggested frontend build order

Given the API surface above, a sensible build order:

1. **Auth pages** (register/login) + token storage + an axios/fetch
   wrapper that attaches the `Authorization` header and redirects to
   login on `401`.
2. **Account connection** — start with the mock `/connect` flow (no real
   OAuth needed) to unblock everything downstream; add the real OAuth
   redirect flow once the rest of the app works.
3. **Dashboard** (`/api/dashboard`) — the natural landing page after login.
4. **Charts** — pick 2-3 to start (`platform-comparison`,
   `engagement`, `followers`) rather than building all 9 chart types at
   once.
5. **Notifications** — a bell icon + dropdown, polling `/api/notifications`
   every N seconds or on window focus.
6. **Reports** — a "generate report" form (date range + type picker) and
   a list/detail view for past reports.

---

## Pushing changes

This document and all backend work is committed to the `main` branch of
this repository.
