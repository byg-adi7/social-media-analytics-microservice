# Audience Insights Platform — Backend Status & Frontend Integration Guide

This document has two purposes: an honest account of what the backend
currently is (for the project report), and a practical reference for
building the frontend against it.

---

## Part 1: Backend Readiness

### Architecture

One Spring Boot service (`analytics-service`) plus a managed Postgres
database. There is no gateway, and no separate Auth or Notification
service — both were folded into `analytics-service` once identity moved
to Supabase Auth and the internal Analytics→Notification call became an
in-process method call instead of a network hop.

```
Frontend (React Native / Expo)
        |
        |  Authorization: Bearer <Supabase JWT>
        v
 analytics-service (:8080 via Docker, :8002 internally)
        |
        v
    postgres (:5432)
```

The frontend also talks to Supabase **directly** for register/login/
logout/password reset — this backend never sees a password, only the JWT
Supabase already issued. Separately, Supabase Auth calls this backend
directly (not via the frontend) on `auth.users` INSERT/UPDATE/DELETE via
Database Webhooks — see "Supabase auth.users webhooks" in the root
`README.md`.

- **Identity** — fully delegated to Supabase Auth. This service only
  verifies the JWT Supabase issues (locally, against Supabase's JWT
  secret) on every request; it never issues, refreshes, or stores
  credentials itself.
- **Analytics Service** (the one deployable) — connects social accounts
  (YouTube, Instagram, TikTok, Facebook, Spotify — real OAuth
  integrations, falling back to mock data by default), accepts CSV
  imports for any platform (including Twitter/X, which has no real API
  integration), syncs/aggregates analytics, serves
  dashboards/charts/reports, creates in-app + push notifications, and
  registers device push tokens.
- One physical Postgres database, managed entirely by versioned Flyway
  migrations (`backend/analytics-service/src/main/resources/db/migration`).

### What's actually done and verified

- **98 automated tests pass** (unit + Spring slice tests) — run with
  `mvn test` inside `backend/analytics-service`.
- **Live-tested against a real running Docker stack**, not just unit
  tests: account connect/sync/CSV-import, dashboard/chart/report
  generation, notification creation + push-preference gating, device
  registration (including the new-device-notification and
  first-device-suppression logic), and all three Supabase webhooks
  (user-created/updated/deleted) including the full account-deletion
  cascade across every user-owned table.
- **CI**: GitHub Actions runs the full unit test suite plus a live
  cross-service integration smoke test (`scripts/smoke-test.sh`) against
  a real Docker Compose stack, on every push to `main`.
- **Schema migrations**: Flyway-managed (`V1`–`V4` so far), not
  `ddl-auto=update` — every schema change is a reviewable, versioned SQL
  file.
- **Secrets**: never committed. `docker-compose.yml` reads them from a
  local `.env` (see `.env.example`) and refuses to start if a required one
  is missing.
- **Security basics in place**: stateless JWT verification (Supabase's
  secret, HMAC-SHA256), CORS allow-list (not a wildcard), consistent JSON
  error bodies (no stack traces leak to clients), in-memory rate limiting
  keyed by user (or IP when unauthenticated), and ownership checks on
  every notification/account/report/device lookup (a user can only ever
  read their own).
- **All 5 real platform integrations** (YouTube, Spotify, Instagram,
  TikTok, Facebook) are real, working OAuth implementations — but see
  "Known gaps" below for what that does and doesn't mean without real
  credentials configured.
- **Notifications**: in-app (list/unread-count/mark-read) plus optional
  push via Firebase Cloud Messaging, gated by a per-user preference. See
  "Supabase auth.users webhooks" and "Push notifications (FCM)" in the
  root `README.md` for setup.

### Known gaps — read this before assuming something works

- **No frontend exists in this repository.** This guide is for building it.
- **The 5 platform integrations need real credentials to do anything
  beyond mock data.** Without a real Google/Meta/Spotify/TikTok developer
  app registered and its credentials set in `.env`, connecting an account
  uses randomly-generated mock analytics, not real platform data. This is
  the default and expected state for local/demo use.
- **Twitter/X has no real API integration at all** (as of 2026, X's API
  has no free tier for new developers — every call costs money with no
  free allowance). Twitter/X data only ever comes in via CSV import.
- **Push notifications need a real Firebase project.** Without one
  (`firebase.enabled=false`, the default), in-app notifications still work
  fully — `NoopFcmPushNotificationService` just skips the push send.
- **No real-time in-app updates.** Notifications are polled via `GET
  /api/notifications` — there is no WebSocket/SSE. The frontend should
  poll periodically (or on app foreground) for the in-app list; actual
  push delivery (when Firebase is configured) still goes through FCM,
  independent of polling.
- **`SUBSCRIPTION_SUCCESS`/`SUBSCRIPTION_EXPIRING`** exist on the
  notification-type enum for forward compatibility only — nothing fires
  them, since this app has no subscription/billing system.
- **No dependency-vulnerability scanning** has been run in this
  environment (no network access to a CVE database at development time).
- **Plain HTTP in local Docker Compose**, no TLS. Render terminates TLS
  in front of the deployed service.
- **In-memory rate limiting only** — correct for a single-instance
  deployment; would need a shared store (e.g. Redis) if ever horizontally
  scaled.

---

## Part 2: What the Frontend Needs to Know

### Running the backend locally

```bash
git clone <this repo>
cd backend
cp .env.example .env
# generate values for the *_SECRET/*_KEY placeholders in .env, e.g.:
openssl rand -base64 32
docker compose up --build
```

The service is then at `http://localhost:8080` — **the frontend should
only ever talk to this URL** locally (or the deployed Render URL in
production).

Everything works immediately with mock data — no platform credentials
are required to build and test the frontend. Swagger UI is available at
`http://localhost:8080/swagger-ui.html` for interactively exploring every
endpoint below.

### Authentication flow

Identity is Supabase Auth, not this backend:

1. The frontend registers/logs in **directly against Supabase** (via the
   `@supabase/supabase-js` SDK or equivalent), using this project's
   Supabase URL + anon key.
2. Supabase returns a JWT (`session.access_token`). Store/refresh it
   however the Supabase client SDK recommends — this backend has no
   opinion on that and no refresh-token endpoint of its own.
3. Send it on **every request to `analytics-service`** as:
   ```
   Authorization: Bearer <supabase-access-token>
   ```
4. This backend verifies the token locally (HMAC against Supabase's JWT
   secret) — it never calls Supabase over the network to validate a
   request. A `401` means the token is missing, expired, or invalid; send
   the user back through the Supabase auth flow.

Password changes, email verification, and password reset are all handled
by Supabase directly — this backend only *reacts* to a subset of
Supabase's own `auth.users` events via webhooks (welcome notification on
signup, password-changed notification, and data cleanup on account
deletion). See the root `README.md`'s "Supabase auth.users webhooks"
section — none of this needs any frontend involvement.

### Standard error response shape

Every error looks like this (fields may be omitted if not applicable):

```json
{
  "timestamp": "2026-07-20T18:30:00",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "VALIDATION_ERROR",
  "message": "Human-readable description",
  "path": "/api/accounts/connect",
  "validationErrors": [
    { "field": "accountId", "message": "must not be blank", "rejectedValue": "" }
  ]
}
```

`validationErrors` only appears on `400` field-validation failures.

`errorCode` is one of: `VALIDATION_ERROR`, `RESOURCE_NOT_FOUND`,
`BAD_REQUEST`, `CONFLICT`, `UNAUTHORIZED`, `FORBIDDEN`,
`PLATFORM_NOT_SUPPORTED`, `ANALYTICS_ERROR`, `EXTERNAL_API_ERROR`,
`INTERNAL_SERVER_ERROR`, `TOO_MANY_REQUESTS`.

Status codes in use: `200`, `201`, `204` (delete/unregister), `400`,
`401`, `404`, `409` (conflict, e.g. duplicate account connection), `429`
(rate limited — in-memory token bucket, keyed per user, default 120
requests/60s), `500`, `502` (`POST /api/reports` only, when the
underlying analytics data lookup fails).

---

### API Reference

All endpoints below require `Authorization: Bearer <token>` unless
explicitly marked public.

#### Accounts (`/api/accounts/*`)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/accounts/connect` | `{platform, accountId, accountName, username?, profileImage?, accessToken, refreshToken?}` | `SocialAccountResponse` (201) |
| GET | `/api/accounts?page=&size=` | — | `PagedResponse<SocialAccountResponse>` (default size 20, sorted by `connectedAt` desc) |
| GET | `/api/accounts/{id}` | — | `SocialAccountResponse` |
| PUT | `/api/accounts/{id}` | `{accountName?, username?, profileImage?, active?}` | `SocialAccountResponse` |
| DELETE | `/api/accounts/{id}` | — | 204 No Content |
| GET | `/api/accounts/{id}/sync` | — | `SocialAccountResponse` (triggers an on-demand sync; fires a `SYNC_SUCCESS` notification) |
| POST | `/api/accounts/import-csv` (multipart) | `platform`, `accountName` (form fields) + `file` | `CsvImportResponse` (201) — creates a new **CSV-imported** account |
| POST | `/api/accounts/{id}/import-csv` (multipart) | `file` | `CsvImportResponse` — merges more rows into an *existing* CSV-imported account, upserting by date; fires an `ANALYSIS_COMPLETED` notification |

`platform` is one of: `YOUTUBE`, `INSTAGRAM`, `TIKTOK`, `FACEBOOK`,
`SPOTIFY`, `TWITTER` (Twitter is CSV-import only — see Known gaps).

CSV format (any column order): `date,followers,views,likes,comments,shares`.

**A user can have both a live OAuth-connected account and a separate
CSV-imported account for the same platform at once** — they are
independent accounts, not alternatives. `connectionType` on
`SocialAccountResponse` (`OAUTH` or `CSV_IMPORT`) tells you which kind
you're looking at; CSV-imported accounts can't be `/sync`'d (only
re-uploaded) and are excluded from the scheduled background sync job.

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
  "active": true,
  "connectionType": "OAUTH"
}
```
Never includes access/refresh tokens.

`CsvImportResponse`: `{account: SocialAccountResponse, rowsInserted, rowsUpdated}`

**In practice, the frontend won't usually call `/connect` directly with a
raw access token** — for a real platform connection, use the OAuth flow
below instead. `/connect` accepting a raw `accessToken` directly is what
lets you simulate a connected mock account for development without going
through a real OAuth provider.

#### OAuth connect flow (per platform)

Identical shape for all 5 real-integration platforms — replace
`{platform}` with `youtube`, `spotify`, `instagram`, `tiktok`, or
`facebook`.

| Method | Path | Auth? | Notes |
|---|---|---|---|
| GET | `/api/oauth/{platform}/authorize` | Yes | Returns `{authorizationUrl}` — redirect the browser here |
| GET | `/api/oauth/{platform}/callback` | **No (public)** | The platform redirects here after consent, not the frontend — never call this directly |

Frontend flow (mirrors `authService.signInWithGoogle()`'s existing pattern
for Supabase's own OAuth):
1. User taps "Connect YouTube" → `GET /api/oauth/youtube/authorize`
   (authenticated) → `{"authorizationUrl": "https://accounts.google.com/..."}`.
2. Open it with `WebBrowser.openAuthSessionAsync(authorizationUrl,
   Linking.createURL('oauth-callback'))` — the second argument tells Expo
   which redirect to watch for so it can auto-close the in-app browser.
3. User consents on the platform's own site.
4. Platform redirects to the backend's `/callback`, which completes the
   connection, then redirects to the mobile deep link configured in
   `{PLATFORM}_FRONTEND_REDIRECT` (default: `audience-insights://oauth-callback`),
   appending `?connected={platform}&accountId={uuid}`.
5. `openAuthSessionAsync` resolves with that final URL — parse
   `connected`/`accountId` off it (same as `result.url` is already parsed
   for `access_token`/`refresh_token` in the Google sign-in flow) to show a
   success message / refresh the account list.

**This is a deep link, not a web page** — there is no localhost:3000 or
any other web frontend involved. `{PLATFORM}_FRONTEND_REDIRECT` must match
a URL scheme the app itself can receive, i.e. `app.json`'s `"scheme"`.

Only produces real data if real credentials for that platform are
configured in the backend's `.env` — otherwise the platform is disabled
and this flow isn't reachable (use the mock `/connect` path for frontend
dev instead).

#### Analytics (`/api/analytics/*`)

All accept optional `startDate`/`endDate` query params (`yyyy-MM-dd`);
some accept `platform` too.

| Method | Path | Query params | Response |
|---|---|---|---|
| GET | `/api/analytics/report` | startDate?, endDate?, platform? | `ReportResponse` — full combined report (summary + comparison + growth + engagement + top posts + best/worst days + recommendations) |
| GET | `/api/analytics/trends` | startDate?, endDate?, platform? | `TrendResponse` — time-series with 7-day moving average |
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

`TrendResponse`: `{labels[], followers[], views[], engagementRate[], movingAverage[], percentageChange, trendDirection}` (`trendDirection` is `UP`/`DOWN`/`STABLE`).

`EngagementResponse`: `{overallEngagementRate, totalLikes, totalComments, totalShares, totalSaves, bestEngagementDay, worstEngagementDay}`.

`GrowthResponse`: `{startFollowers, endFollowers, followerDifference, growthRatePercentage, averageDailyGrowth, averageWeeklyGrowth, averageMonthlyGrowth, predictedFollowersNext30Days}`.

#### Charts (`/api/charts/*`)

All return chart-ready JSON (no image/SVG rendering — that's the
frontend's job).

| Method | Path | Response shape |
|---|---|---|
| GET | `/api/charts/engagement` | `MultiLineChartResponse`: `{labels[], series: {platform: number[]}}` |
| GET | `/api/charts/followers` | same shape |
| GET | `/api/charts/views` | same shape |
| GET | `/api/charts/platform-comparison` | `[{platform, followers}]` |
| GET | `/api/charts/engagement-distribution` | `[{platform, value}]` (pie chart) |
| GET | `/api/charts/top-content?limit=10` | `[{platform, title, publishedDate, views, likes, comments, shares, engagementRate}]` |
| GET | `/api/charts/audience-demographics` | `[{platform, accountId, accountName, byAgeRange, byGender, byCity, byCountry}]` — maps may be empty per-platform, see limitations below |
| GET | `/api/charts/weekly-growth` | `{labels[], followerGrowth[]}` |
| GET | `/api/charts/monthly-growth` | same shape |

**Audience demographics limitations the frontend must handle
gracefully** (empty maps are normal, not a bug):
- Facebook: no age/gender data at all (Meta platform restriction since
  March 2024) — only city/country.
- TikTok: no demographics at all via this API tier — always empty.
- YouTube/Spotify: not exposed by their public APIs — always empty.
- Instagram: full data, but empty if the account has under ~100
  followers (Meta's own minimum).
- CSV-imported accounts: always empty (no client to fetch demographics from).

#### Dashboard (`/api/dashboard`)

| Method | Path | Response |
|---|---|---|
| GET | `/api/dashboard` | `{totalFollowers, totalViews, totalLikes, totalComments, totalShares, totalReach, totalImpressions, averageEngagement, connectedPlatforms: string[], bestPerformingPlatform, lastSyncTime}` |

#### Notifications (`/api/notifications/*`)

Notifications are always system-generated — there is no endpoint for a
user to create their own. List/unread-count reflect in-app notifications
only; whether a push notification was *also* sent depends on
`firebase.enabled` server-side and the user's own preference below.

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/notifications?page=&size=` | — | `PagedResponse<NotificationResponse>`, most recent first |
| GET | `/api/notifications/unread-count` | — | `{unreadCount}` |
| PATCH | `/api/notifications/{id}/read` | — | Updated `NotificationResponse` |
| PATCH | `/api/notifications/read-all` | — | `{markedAsRead}` |
| GET | `/api/notifications/preferences` | — | `{pushEnabled, emailEnabled}` (defaults to both `true` if never set) |
| PUT | `/api/notifications/preferences` | `{pushEnabled, emailEnabled}` | Updated preferences |

`NotificationResponse`:
```json
{
  "id": "uuid", "type": "ACCOUNT_CONNECTED", "title": "Account connected",
  "message": "Your YouTube account was connected successfully.",
  "data": "{\"accountId\":\"...\"}", "read": false,
  "createdAt": "2026-07-20T18:30:00", "readAt": null
}
```
`data` is an opaque JSON string (or `null`) for deep-linking (e.g.
`{"accountId": "..."}` or `{"reportId": "..."}`) — its shape varies by `type`.

`type` is one of `WELCOME`, `ACCOUNT_CONNECTED`, `ANALYSIS_COMPLETED`,
`SYNC_SUCCESS`, `SYNC_FAILURE`, `REPORT_READY`, `PASSWORD_CHANGED`,
`NEW_DEVICE_LOGIN`. (`SUBSCRIPTION_SUCCESS`/`SUBSCRIPTION_EXPIRING` exist
on the enum too but are never fired — see Known gaps.)

**Emailing (`emailEnabled`) is preference-only right now** — there is no
email-sending system in this backend yet, so toggling it has no
observable effect beyond being echoed back by this API.

#### Device registration — push notifications (`/api/devices/*`)

Call `register` once per app launch (safe to call every time — it just
refreshes the token) and `unregister` on logout.

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/devices/register` | `{token, platform}` | `DeviceTokenResponse` (201) |
| DELETE | `/api/devices/unregister` | `{token}` | 204 No Content |

`platform` is `IOS`, `ANDROID`, or `WEB`. `token` is the Expo/Firebase FCM
device push token obtained from `expo-notifications` (or the native
Firebase SDK) on the client. Registering a second (or later) device for
an already-known user fires a `NEW_DEVICE_LOGIN` notification (suppressed
on the very first device, to avoid duplicating the `WELCOME`
notification). If the backend has no Firebase project configured
(`firebase.enabled=false`), registration still succeeds and is stored —
it just won't receive any actual pushes yet.

`DeviceTokenResponse`: `{id, platform, active, createdAt, lastUsedAt}` (never echoes the token itself back).

#### Reports (`/api/reports/*`)

On-demand CSV reports, generated from the same data the Analytics
endpoints above expose.

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/reports` | `{reportType, startPeriod, endPeriod}` (dates as `yyyy-MM-dd`) | Full report incl. CSV `content` (201) |
| GET | `/api/reports?page=&size=` | — | `PagedResponse<ReportSummaryResponse>` (no `content` field — fetch by id for that) |
| GET | `/api/reports/{id}` | — | Full report incl. `content` |
| GET | `/api/reports/{id}/download` | — | The CSV as an actual downloadable file (`Content-Disposition: attachment`), not wrapped in JSON |

`reportType` is `PLATFORM_COMPARISON` or `SUMMARY`. `status` is
`COMPLETED` or `FAILED`. `content` is a raw CSV string when `status` is
`COMPLETED` — the frontend can render it inline or offer the `/download`
link directly (e.g. as an `<a href>` / `Linking.openURL` target, since
it's a real file response, not JSON).

If the underlying analytics data lookup fails while generating a report,
`POST /api/reports` itself returns `502` (`EXTERNAL_API_ERROR`) rather
than a raw `500` — but a `FAILED` report row is still saved first (with
`errorMessage` populated), so it shows up in the list even though the
`POST` call errored. Show a retry option on `502`, don't treat it as a
permanent failure.

---

### Suggested frontend build order

1. **Supabase auth pages** (register/login) using the Supabase client SDK
   directly + an axios/fetch wrapper that attaches
   `Authorization: Bearer <supabase-access-token>` to every request to
   this backend, and redirects to login on `401`.
2. **Account connection** — start with the mock `/connect` flow (no real
   OAuth needed) to unblock everything downstream; add the real OAuth
   redirect flow once the rest of the app works. Add CSV import as an
   alternate path (useful for Twitter/X, or any platform without
   credentials configured).
3. **Dashboard** (`/api/dashboard`) — the natural landing page after login.
4. **Charts** — pick 2-3 to start (`platform-comparison`, `engagement`,
   `followers`) rather than building all 9 chart types at once.
5. **Notifications + push** — a bell icon + dropdown polling
   `/api/notifications`, plus `expo-notifications` registering the device
   token via `POST /api/devices/register` on launch and a settings toggle
   wired to `PUT /api/notifications/preferences`.
6. **Reports** — a "generate report" form (date range + type picker), a
   list/detail view for past reports, and a download button hitting
   `/api/reports/{id}/download`.

---

## Pushing changes

This document and all backend work is committed to the `main` branch of
this repository.
