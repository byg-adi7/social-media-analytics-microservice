# Audience Insights — Backend Documentation

This is the complete reference for the backend of Audience Insights, a
platform that lets content creators connect their social media accounts
and view unified analytics, reports, and notifications from one place.
It covers what the system does (for a non-technical reader) and exactly
how it works (for a developer), plus an honest, current list of what it
does not yet do.

---

# Part 1 — What This Backend Does (Plain-Language Overview)

## The problem it solves

A content creator active on YouTube, Instagram, TikTok, Facebook, Spotify,
and X/Twitter has to check six different apps to know how they're doing.
Audience Insights connects those accounts once and gives the creator one
dashboard: total followers, views, engagement, growth trends, their
best-performing posts, and how each platform compares to the others.

## Who uses it

Individual creators and small creator teams who want a single place to
track performance across platforms, without needing a data analyst or a
spreadsheet.

## What a user can actually do

- **Connect social accounts.** Either through a real "Sign in with
  [Platform]" flow (YouTube, Spotify, Instagram, TikTok, Facebook today),
  or by uploading a CSV of daily numbers for any platform — including
  X/Twitter, which has no OAuth integration here (see Part 3).
- **See one dashboard** combining every connected account: total
  followers, views, likes, comments, shares, reach, impressions, average
  engagement rate, and which platform is performing best.
- **Explore 9 different charts**: engagement over time, follower growth,
  views over time, a platform-vs-platform bar chart, an engagement-share
  pie chart, top-performing posts, audience age/gender/location
  breakdowns (where the platform allows it), and weekly/monthly growth.
- **Generate and download reports** — a CSV summary or platform
  comparison for any date range, viewable in-app or downloaded as an
  actual file.
- **Get notified** in-app, and optionally as a push notification on their
  phone, when: they sign up, an account connects, a sync succeeds or
  fails, a CSV upload finishes being analyzed, a report is ready, their
  password changes, or a new device signs into their account.
- **Control their own notification preferences** — push notifications can
  be turned off without losing the in-app notification history.
- **Have their data actually deleted** when they delete their account —
  every account, analytics row, notification, report, device token, and
  preference tied to them is removed, automatically, the moment their
  account is deleted at the identity layer (Supabase).

## What happens automatically, without the user doing anything

- Every connected account (that came from a real platform connection, not
  a CSV upload) is re-synced **automatically, once an hour**, so the
  dashboard stays current without the user manually refreshing.
- If a scheduled sync fails for an account, the user gets a notification
  telling them so — they're never left wondering why their numbers look
  stale.

---

# Part 2 — Developer Documentation

## Tech stack

Java 21 · Spring Boot 3.3 · Maven · PostgreSQL 14+ · Spring Data JPA ·
Spring Security · Spring Validation · Flyway · JJWT · Bucket4j (rate
limiting) · Firebase Admin SDK (push notifications) · Lombok · MapStruct
· springdoc-openapi (Swagger) · Docker · JUnit 5 · Mockito · H2 (tests
only) · GitHub Actions (CI) · Render (hosting).

## Architecture

**One deployable service** — `analytics-service` — plus a managed
PostgreSQL database. There is no API gateway and no separate Auth or
Notification service. Both used to exist as standalone microservices and
were folded into this one service: the Auth Service shrank to nothing
once identity moved to Supabase Auth (this backend only *verifies* a
Supabase-issued JWT locally; it never issues or stores one), and the
Notification Service's only external caller was this same service, so
their network call became a plain in-process method call. This
consolidation is also what makes free-tier hosting realistic — Render's
free instance type only covers Web Services, Postgres, and Key Value, not
the private services/background workers a multi-service topology would
have needed.

```
                     Frontend (mobile app)
                              |
          Authorization: Bearer <Supabase JWT>
                              v
                     analytics-service
                    (Spring Boot, :8002)
                              |
                              v
                    PostgreSQL (:5432)

Supabase Auth (external, not part of this repo) calls analytics-service
directly over HTTP via Database Webhooks whenever a user is created,
updated, or deleted in auth.users.
```

### Package layout

Two top-level Java packages inside one Spring Boot application:

- **`com.platform.analytics`** — accounts, sync, analytics
  calculations, charts, dashboard, security/JWT verification, rate
  limiting, Supabase webhooks, and one sub-package per real platform
  integration (`youtube`, `spotify`, `instagram`, `tiktok`, `facebook`),
  each self-contained (its own OAuth service, API client, DTOs,
  controller, and `*Properties` config class).
- **`com.platform.notification`** — in-app + push notifications, device
  token registration, notification preferences, and on-demand CSV
  reports. A sibling package, not nested under `com.platform.analytics` —
  it calls back into `AnalyticsQueryService` directly (in-process) to
  build report content.

Within each package, a consistent layered structure:

```
controller  →  service (interface)  →  service.impl  →  repository  →  entity
                                              ↑
                                    mapper (MapStruct) / client (Feign, Mock)
```

- **controller** — REST endpoints and Swagger annotations only, no
  business logic.
- **service / service.impl** — business logic and orchestration, behind
  an interface so callers depend on a contract, not an implementation.
- **repository** — Spring Data JPA repositories.
- **entity** — JPA entities.
- **dto.request / dto.response** — API contracts, decoupled from
  entities (tokens are never serialized into a response, for example).
- **exception** — typed exceptions + one `GlobalExceptionHandler`
  producing a consistent JSON error shape for every error in the app.
- **config** — Security, CORS, rate limiting, async executor, Firebase,
  shared OAuth state signing, JPA repository/entity scanning (kept
  separate from the main `@SpringBootApplication` class specifically so
  it doesn't leak into `@WebMvcTest` slices, which have no datasource).

### Extensibility: adding a new platform

1. Add a `Platform` enum constant.
2. Add a new top-level package (e.g. `com.platform.analytics.linkedin`)
   containing that platform's OAuth service, API client, DTOs, and a
   `SocialMediaClient` implementation, following the `youtube` package as
   the reference pattern.
3. Register the client as a Spring bean gated behind a
   `<platform>.enabled` property via `@ConditionalOnProperty`.

No controller, service, or repository code outside that new package needs
to change — the sync scheduler, calculations, and chart endpoints all
iterate over `Platform.values()` and any bean implementing
`SocialMediaClient`.

---

## Data model

**`social_accounts`** — one row per connected account (a user can have
more than one for the same platform: one live-synced, one CSV-imported).

| Field | Notes |
|---|---|
| id (UUID, PK) | |
| userId | owning creator (no DB-level FK — see below) |
| platform | `YOUTUBE`, `INSTAGRAM`, `TIKTOK`, `FACEBOOK`, `SPOTIFY`, `TWITTER` |
| connectionType | `OAUTH` or `CSV_IMPORT` |
| accountId, accountName, username, profileImage | |
| accessToken, refreshToken | never included in any API response |
| connectedAt, lastSynced | |
| active | |

Unique constraint: `(platform, accountId)`.

**`analytics`** — one row per account per day; the time-series foundation
for every trend/growth/chart calculation.

| Field | Notes |
|---|---|
| id (UUID, PK), socialAccountId (FK) | |
| analyticsDate | |
| followers, following, impressions, reach, profileVisits, views, watchTime | |
| likes, comments, shares, saves, posts | |
| engagementRate | computed on write |
| createdAt | |

Unique constraint: `(socialAccountId, analyticsDate)`.

**`notifications`**

| Field | Notes |
|---|---|
| id (UUID, PK), userId | |
| type | see NotificationType below |
| title, message | `title` is a short heading, `message` the full text |
| data | opaque JSON string for frontend deep-linking, nullable |
| isRead, readAt | `readAt` is null until marked read |
| createdAt | |

**`device_tokens`** — Firebase Cloud Messaging push tokens.

| Field | Notes |
|---|---|
| id (UUID, PK), userId | |
| token | globally unique (not per-user) — re-registering the same token, even under a different user, reassigns the row rather than duplicating it |
| platform | `IOS`, `ANDROID`, `WEB` |
| active | false after unregister, or after FCM reports it invalid |
| createdAt, lastUsedAt | |

**`notification_preferences`** — one row per user, created lazily on
first read/write.

| Field | Notes |
|---|---|
| id (UUID, PK), userId (unique) | |
| pushEnabled | gates the push channel only; the in-app row is always created |
| emailEnabled | stored, but no email-sending system exists yet (see Part 3) |
| createdAt, updatedAt | |

**`reports`** — on-demand generated CSV reports.

| Field | Notes |
|---|---|
| id (UUID, PK), userId | |
| reportType | `PLATFORM_COMPARISON` or `SUMMARY` |
| startPeriod, endPeriod | |
| status | `COMPLETED` or `FAILED` |
| content | the CSV itself, null if `FAILED` |
| errorMessage | populated only if `FAILED` |
| generatedAt | |

`userId` on every table above is a plain UUID column, not a foreign key —
Supabase (a separate system) owns the actual `users` table, and a real FK
across systems would couple this service's schema to Supabase's. Deleting
a user's data on account deletion is handled explicitly instead (see
"Account deletion cleanup" below).

### Migrations

Flyway-managed, in `backend/analytics-service/src/main/resources/db/migration`:
`V1__baseline.sql` (initial schema), `V2__notification_tables.sql`,
`V3__account_connection_type.sql`, `V4__notification_push_and_preferences.sql`.
Hibernate's `ddl-auto` is set to `validate`, not `update` — Flyway is the
only thing that ever changes the schema; Hibernate just confirms the
entity mappings agree with what Flyway produced.

---

## Business logic & rules

### Analytics calculations (`AnalyticsCalculator` — pure, stateless, unit-tested)

- **Engagement Rate** = `(Likes + Comments + Shares) / Followers × 100`, rounded to 2 decimals. `0` if followers is `0` (never divides by zero).
- **Growth Rate (%)** = `(End − Start) / Start × 100`. If the starting value is `0`, defined as `100%` if the ending value is positive, else `0%` (avoids a divide-by-zero on a brand-new account).
- **Moving Average** — simple moving average over a configurable window (7 days for follower trends), using a shrinking window at the start of the series rather than padding with zeros.
- **Percentage Increase/Decrease** — same zero-start handling as Growth Rate.
- **Trend Direction** — `UP` if percentage change > 1%, `DOWN` if < −1%, otherwise `STABLE`. The ±1% band exists so tiny day-to-day noise doesn't flip the label back and forth.
- **30-day follower prediction** = `endFollowers + (averageDailyGrowth × 30)`, where `averageDailyGrowth` is the total follower difference over the query range divided by the number of days spanned (minimum 1 day, to avoid divide-by-zero on a single-day range). This is a linear extrapolation of the recent trend, not a statistical model.

### Cross-platform aggregation rules

- **Best / worst performing platform** = highest / lowest average engagement rate across that platform's connected accounts in the query range.
- **Fastest-growing platform** = highest growth rate (start-vs-end followers) in range.
- **Most active platform** = most posts summed in range. **Most viewed platform** = most views summed in range.
- **Dashboard's "best performing platform"** uses each platform's single most recent `Analytics` row per account (not a range average) — it answers "who's doing best right now," not "who did best over this period," which is what the Analytics/Summary endpoints answer instead.
- **Best/worst engagement days** = the top/bottom 3 calendar dates by average engagement rate across all accounts, within the query range.
- **Auto-generated recommendations** (part of `GET /api/analytics/report`) are simple rule-based sentences, not machine-learning output: growth < 1% → suggests posting more on the most-active platform; a clear worst-performing platform → suggests trying new formats there; overall engagement < 2% → suggests prioritizing comment/share-driving content; a clear best-performing platform → suggests repurposing its content elsewhere. These are deterministic, explainable rules, not a black-box model.

### Account connection rules

- A `(platform, accountId)` pair is globally unique — not merely unique per user. Attempting to connect an account that's already connected by *any* user (not just the current one) is rejected with a clean `400`, rather than surfacing a raw database constraint violation.
- **A user may have both a live OAuth-connected account and a separately CSV-imported account for the same platform at once** — these are two independent `social_accounts` rows, not alternative states of one account. This lets a user get real-time data from a connected account while also backfilling historical data via CSV for the same platform.
- **CSV-imported accounts are excluded from the hourly scheduled sync** and cannot be manually `/sync`'d — attempting to trigger one returns a `400` explaining the account is CSV-based. `MockSocialMediaClient` would otherwise silently overwrite manually-uploaded data with fabricated numbers, or a real client would crash trying to use a synthetic access token — this exclusion is the deliberate fix for that.
- **CSV re-uploads upsert by date**: a row for a date already present in that account's history is *updated* in place; a new date is *inserted*. Re-uploading the same file twice is idempotent (`rowsUpdated` = all rows, `rowsInserted` = 0 the second time).
- Disconnecting an account deletes its `analytics` rows first, then the account itself, respecting the foreign key.

### Report generation rules

- Generating a report (`POST /api/reports`) reads live data from the same aggregation logic the Analytics endpoints use — a report is never a separately-maintained copy of the numbers.
- **If the underlying analytics data lookup fails, a `FAILED` report row is still saved** (with an error message), and the `POST` call itself returns `502`. The failure is visible in the report list even though the request that triggered it errored — the row isn't silently lost.
- Report `content` (CSV) is only ever generated once, at creation time, and stored — `GET`/`download` never regenerates it, so what a user downloaded yesterday is identical to what they'd see today, even if the underlying analytics have since changed.

### Notification & push rules

- See "Notifications & push (FCM)" above for exactly which event fires which notification type.
- **`notifyUser(...)` always persists the in-app notification, regardless of the user's push preference** — a preference of `pushEnabled: false` only skips the FCM send, it never suppresses the in-app row. This means the in-app bell/list is always a complete history, independent of push settings.
- **A device token is globally unique, not per-user.** Registering a token already on file — whether the same user relaunching the app, or a *different* user signing into the same physical device after a logout — reassigns that one row to the new registration rather than creating a duplicate or leaving it pointed at the previous owner.
- **`NEW_DEVICE_LOGIN` is suppressed on a user's very first device**, specifically to avoid firing alongside `WELCOME` on a brand-new signup; every device after the first does trigger it.

### Account-deletion rule

- Supabase, not this backend, owns the actual delete of a user's identity. This backend only *reacts* to that deletion (via a Database Webhook) by deleting every row **it** owns for that `userId` — social accounts, analytics, notifications, reports, device tokens, and preferences — as one atomic transaction. Partial cleanup (e.g. accounts deleted but notifications left behind) is not possible; either all of it is deleted or the whole webhook call fails and Supabase's own retry behavior applies.

---

## API reference

Every endpoint requires `Authorization: Bearer <Supabase JWT>` unless
marked public. Full interactive docs at `/swagger-ui.html` once running.

### Accounts (`/api/accounts`)
`POST /connect`, `GET` (paginated), `GET /{id}`, `PUT /{id}`,
`DELETE /{id}`, `GET /{id}/sync` (on-demand sync), `POST /import-csv`
(multipart, new CSV-imported account), `POST /{id}/import-csv` (multipart,
merge more rows into an existing CSV-imported account, upserting by date).

### OAuth connect flow (`/api/oauth/{platform}`)
`youtube` / `spotify` / `instagram` / `tiktok` / `facebook`. `GET
/authorize` (authenticated, returns the provider's consent URL); `GET
/callback` (**public** — the provider redirects the user's raw browser
here, which carries no `Authorization` header; the user is instead
identified via a signed `state` parameter, not a JWT).

### Analytics (`/api/analytics`)
`GET /report` (full combined report), `/trends`, `/platform-comparison`,
`/summary`, `/top-platform`, `/engagement`, `/growth` — all accept
optional `startDate`/`endDate`/`platform` query params.

### Charts (`/api/charts`)
`GET /engagement`, `/followers`, `/views` (multi-line), `/platform-comparison`
(bar), `/engagement-distribution` (pie), `/top-content`,
`/audience-demographics`, `/weekly-growth`, `/monthly-growth`.

### Dashboard (`/api/dashboard`)
`GET` — one aggregated KPI object.

### Notifications (`/api/notifications`)
`GET` (paginated), `GET /unread-count`, `PATCH /{id}/read`,
`PATCH /read-all`, `GET /preferences`, `PUT /preferences`.

### Devices (`/api/devices`)
`POST /register` (`{token, platform}` — upserts by token),
`DELETE /unregister` (`{token}`).

### Reports (`/api/reports`)
`POST` (generate), `GET` (paginated summaries), `GET /{id}` (full,
includes CSV content), `GET /{id}/download` (the CSV as an actual
downloadable file, not JSON).

### Webhooks (`/api/webhooks`) — **all public**, not for frontend use
Called directly by Supabase Database Webhooks on the `auth.users` table,
authenticated by a shared secret header (`X-Webhook-Secret`) instead of a
JWT:
- `POST /user-created` (INSERT) → sends a `WELCOME` notification.
- `POST /user-updated` (UPDATE) → compares the row's `encrypted_password`
  before/after; only an actual password change (not any other field
  update) sends a `PASSWORD_CHANGED` notification. The hash values are
  only ever compared, never logged or persisted.
- `POST /user-deleted` (DELETE) → deletes every row this service owns for
  that user — accounts, analytics, notifications, reports, device
  tokens, and preferences — in one transaction.

### Standard error shape

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

`errorCode` is one of `VALIDATION_ERROR`, `RESOURCE_NOT_FOUND`,
`BAD_REQUEST`, `CONFLICT`, `UNAUTHORIZED`, `FORBIDDEN`,
`PLATFORM_NOT_SUPPORTED`, `ANALYTICS_ERROR`, `EXTERNAL_API_ERROR`,
`INTERNAL_SERVER_ERROR`, `TOO_MANY_REQUESTS`.

---

## Security

- **Identity**: fully delegated to Supabase Auth. This service verifies
  the JWT it's given locally (HMAC-SHA256 against Supabase's JWT secret)
  on every request — it never calls Supabase over the network to
  validate a token, and never issues, stores, or refreshes one itself.
- **Ownership checks**: every notification/account/report/device lookup
  is scoped by the authenticated user's own id (from the verified JWT),
  never a client-supplied id — a user can only ever read or modify their
  own data.
- **Rate limiting**: in-memory Bucket4j token bucket, keyed by
  authenticated user id (falling back to client IP for anonymous
  requests), default 120 requests per 60 seconds, excluding
  `/actuator/health`, `/swagger-ui`, and `/v3/api-docs`. Appropriate for
  a single-instance deployment; would need a shared store (e.g. Redis) if
  ever horizontally scaled.
- **CORS**: an explicit allow-list (`CORS_ALLOWED_ORIGINS`), not a
  wildcard.
- **Webhook authentication**: the 3 Supabase webhook endpoints are public
  (no JWT possible — Supabase calls them directly) but require a shared
  secret header, compared in constant time to resist timing attacks.
- **Secrets**: never committed to git. Read from environment variables
  with no real default in production (`docker-compose.yml` refuses to
  start if a required one is missing).
- **Consistent error bodies**: no stack traces or internal details ever
  leak to a client, in any error path.

---

## Notifications & push (FCM)

- **Types**: `WELCOME`, `ACCOUNT_CONNECTED`, `ANALYSIS_COMPLETED`,
  `SYNC_SUCCESS`, `SYNC_FAILURE`, `REPORT_READY`, `PASSWORD_CHANGED`,
  `NEW_DEVICE_LOGIN` are all wired to real events. `SUBSCRIPTION_SUCCESS`/
  `SUBSCRIPTION_EXPIRING` exist on the enum for forward compatibility
  only — see Part 3.
- **Where each type fires**: `WELCOME`/`PASSWORD_CHANGED` — Supabase
  webhooks. `ACCOUNT_CONNECTED` — OAuth connect or CSV import (first
  upload). `ANALYSIS_COMPLETED` — a CSV re-upload merging more rows into
  an existing account. `SYNC_SUCCESS` — a user-triggered on-demand sync
  only, deliberately **not** the routine hourly scheduled batch (which
  would otherwise spam a notification every single day). `SYNC_FAILURE`
  — the scheduled batch job, when a sync attempt fails. `REPORT_READY` —
  report generation finishing. `NEW_DEVICE_LOGIN` — registering a device
  token that's new for that user, suppressed on the user's very first
  device (to avoid duplicating `WELCOME`).
- **Two channels, one call**: `NotificationService.notifyUser(...)`
  always persists the in-app notification (synchronously, in the
  caller's transaction) and additionally fans a push notification out
  asynchronously (via a dedicated thread pool, so a slow/failed push
  never blocks or fails the caller) — but only if the user hasn't
  disabled push in their preferences. The in-app row is created either
  way.
- **Push delivery**: real, via the Firebase Admin SDK, gated behind
  `firebase.enabled` (default `false`) exactly like every other platform
  integration in this codebase. With it disabled,
  `NoopFcmPushNotificationService` handles the push channel — i.e. does
  nothing — so the rest of the pipeline (in-app list, unread count, mark
  read) works fully with zero Firebase setup. Sends one message per
  device token (not a multicast), so one invalid token never blocks
  delivery to a user's other devices; a token FCM reports as unregistered
  is automatically deactivated.

---

## Configuration reference (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | 8002 | HTTP port |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | localhost / 5432 / analytics_db / postgres / postgres | PostgreSQL connection |
| `SUPABASE_JWT_SECRET` | dev-only placeholder | Verifies Supabase-issued JWTs locally — must be the real project secret in production |
| `OAUTH_STATE_SECRET` | dev-only placeholder | Signs the OAuth `state` parameter shared by every platform's connect flow |
| `SUPABASE_WEBHOOK_SECRET` | dev-only placeholder | Shared secret for all 3 `/api/webhooks/*` endpoints |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated allow-list |
| `RATE_LIMIT_ENABLED` / `RATE_LIMIT_CAPACITY` / `RATE_LIMIT_REFILL_PERIOD_SECONDS` | true / 120 / 60 | Token-bucket rate limiting |
| `FIREBASE_ENABLED` | false | Turns on real FCM push (off = no-op push sender) |
| `FIREBASE_SERVICE_ACCOUNT_BASE64` | — | Base64-encoded Firebase service-account JSON key (recommended for containers/PaaS) |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | — | Local-dev alternative: a filesystem path to the same JSON key |
| `SYNC_ENABLED` / `SYNC_CRON` | true / `0 0 * * * *` | Scheduled sync job on/off and frequency (hourly by default) |
| `MOCK_DATA_ENABLED` | true | Whether `MockSocialMediaClient` is available as a fallback |
| `{PLATFORM}_INTEGRATION_ENABLED` | false | Per-platform master switch (YOUTUBE/SPOTIFY/INSTAGRAM/TIKTOK/FACEBOOK) |
| `{PLATFORM}_CLIENT_ID` / `_CLIENT_SECRET` (or `_KEY` for TikTok, `_APP_ID` for Facebook) | — | Per-platform OAuth credentials |
| `{PLATFORM}_REDIRECT_URI` | `http://localhost:8002/api/oauth/{platform}/callback` | Must match the value registered on that platform's developer console |
| `{PLATFORM}_FRONTEND_REDIRECT` | `http://localhost:3000/dashboard` | Where the browser lands after a successful connect |

---

## Real platform integrations — what's real vs. approximated

Every platform starts on `MockSocialMediaClient` (realistic synthetic
data) until its `*_INTEGRATION_ENABLED` flag is turned on with valid
credentials — at which point `SocialMediaClientResolver` automatically
prefers the real client, with no other code change.

| Platform | Real | Approximated | Not available |
|---|---|---|---|
| **YouTube** | Channel identity, subscriber count, channel views, video count, top-content stats, token refresh | Per-day engagement deltas, watch time (Data API only exposes cumulative totals; true deltas need the separate YouTube Analytics API) | — |
| **Spotify** | The connected account's own follower/following count, recently-played-based view/watch-time proxy, top tracks, token refresh | `views`/`watchTime` (rolling ≤50-item recently-played window, not full history) | impressions, reach, profileVisits, likes, comments, shares, saves, posts (no equivalent for a personal account); real audience/streaming analytics (Spotify restricts that to label/distributor deals) |
| **Instagram** | Followers/following/posts, reach/views/likes/comments/shares/saves (account-level insights), top-content stats, full age/gender/city/country demographics, token refresh | — | impressions (Meta deprecated it), profileVisits (Meta deprecated it), watchTime |
| **TikTok** | Followers/following/posts, cumulative likes, top-content stats, token refresh | views/comments/shares (summed across the most recent ~20 videos, no account-level total exists) | impressions, reach, profileVisits, watchTime, saves; audience demographics (only available via a separate Ads/Marketing API this integration doesn't use) |
| **Facebook** | Followers, views, reach, likes (Page Insights), top-content view counts | comments/shares (summed across recent posts, no account-level total) | impressions (metric retired), profileVisits, watchTime, saves; age/gender demographics (Meta blocks this for any app connection made after March 2024); no token refresh needed — Page tokens don't expire under normal conditions |
| **Twitter/X** | — (CSV import only) | — | No OAuth/API integration exists at all — see Part 3 |

Every "not available" field above is genuinely absent from that
platform's public API, not a bug — each is documented in code precisely
so it's never silently misrepresented as real data.

---

## Testing

`mvn test` inside `backend/analytics-service` runs the full suite (98
tests as of this writing): unit tests (calculation formulas, OAuth state
signing), Mockito service tests (every `*ServiceImpl`), `@DataJpaTest`
repository tests against H2, `@WebMvcTest` controller-slice tests, and
`@SpringBootTest` full-context integration tests (including a dedicated
regression test guarding against a past `LazyInitializationException` on
the dashboard/chart endpoints). CI (GitHub Actions) runs this same suite
plus a live cross-service smoke test against a real Docker Compose stack
on every push to `main`.

---

## Deployment

- **Local**: `docker compose up --build` from the repo root (needs a
  `.env` — see `.env.example`). Multi-stage Docker build (Maven builds
  the jar in one stage, a minimal `eclipse-temurin:21-jre-alpine` runs it
  in the next, as a non-root user, with a container `HEALTHCHECK` against
  `/actuator/health`).
- **Production**: Render, via the `render.yaml` Blueprint at the repo
  root — one free Web Service (this Docker image) plus one free managed
  Postgres database. Secrets marked `sync: false` in that file are never
  committed; Render prompts for them once when the blueprint is applied.

---

# Part 3 — Known Limitations

Read this before assuming something works end-to-end.

### Platform integrations
- **All 5 real platform integrations need real developer credentials to
  do anything beyond mock data.** Without them, every connected account
  uses randomly-generated simulated numbers, not real platform data —
  this is the default and expected state for local/demo use.
- **Instagram/Facebook/TikTok integrations require the platform's own App
  Review** (and, for Instagram, Meta Business Verification) before real
  users beyond the app's own registered testers can connect — a platform
  requirement on Meta's/TikTok's side, not something this codebase can
  bypass.
- **Spotify requires the connecting account to have Premium**, and its
  "Development Mode" API access caps out at 5 authorized users total
  until Spotify grants Extended Quota Mode (which itself requires
  250,000+ monthly active users) — a hard platform ceiling, not a bug.
- **Twitter/X has no real API integration at all.** As of 2026, X's API
  has no free tier for new developers — every call, even reading a
  follower count, costs real money with no free allowance. Twitter/X
  data only ever enters the system via CSV import; there is no OAuth
  connect flow for it and none is planned unless that pricing changes.
- Several metrics are **structurally unavailable per platform**, not
  missing by oversight — see the "Real platform integrations" table
  above for exactly which ones, per platform.

### Notifications & push
- **Push notifications require a real Firebase project.** With none
  configured (`firebase.enabled=false`, the default), in-app notifications
  still work fully — the push send is just skipped.
- **No real-time delivery of the in-app list itself.** The frontend polls
  `GET /api/notifications`; there is no WebSocket/SSE. (Actual push
  delivery, when Firebase is configured, is independent of this and does
  arrive in real time via FCM.)
- **`emailEnabled` in notification preferences has no effect yet.**
  There is no email-sending system in this backend — the field is stored
  and returned by the API, but nothing reads it to decide whether to send
  an email, because nothing sends email.
- **`SUBSCRIPTION_SUCCESS`/`SUBSCRIPTION_EXPIRING`** exist on the
  notification-type enum for forward compatibility only. Nothing fires
  them, because there is no subscription/billing system in this app (see
  below).

### Business features not built
- **No billing/subscriptions system.** A `subscriptions` table exists
  only in a deprecated, no-longer-applied seed script
  (`database/schema.sql`) — no service reads or writes it. There is no
  payment integration (e.g. Stripe) and no plan-based feature gating.
- **No admin/permission system.** Every authenticated user has the same
  capabilities; there is no admin role.

### Infrastructure & scale
- **In-memory rate limiting only.** Correct for the current
  single-instance deployment; horizontally scaling this service would
  need a shared store (e.g. Redis) instead.
- **Render's free Postgres tier auto-expires.** Render's free database
  plan is deleted 30 days after creation plus a 14-day grace period, with
  no backup — an accepted, deliberate risk for this project's bounded
  academic timeline, not something silently worked around by migrating
  providers.
- **No dependency-vulnerability (CVE) scanning** has been run in this
  development environment (no network access to a CVE database at
  development time).
- **Plain HTTP in local Docker Compose**, no TLS — Render terminates TLS
  in front of the deployed service in production.
- **The OAuth `state` parameter is signed in-request (HMAC), not stored
  server-side.** Fine for a single instance; would need a shared store
  (e.g. Redis) if scaled to multiple instances behind a load balancer
  without sticky sessions.

### Data & analytics depth
- **No pagination on `/api/analytics/trends`** for very large date
  ranges — every other list endpoint (`accounts`, `notifications`,
  `reports`) is paginated, this one is not yet.
- **No caching layer** (e.g. Redis) in front of dashboard/report
  endpoints — every request recomputes from the database directly.
- **No domain events / message broker.** Nothing here publishes events
  like "account connected" or "sync completed" for another system to
  consume; all effects (notifications, etc.) happen as direct in-process
  calls.
