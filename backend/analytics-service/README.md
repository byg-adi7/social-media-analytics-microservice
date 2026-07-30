# Analytics Service

Analytics microservice for the **Audience Insights Platform** — a multi-service SaaS product that lets content creators connect multiple social media accounts and view unified analytics from one dashboard.

This service owns **everything**: connecting accounts, synchronizing metrics, aggregating data, computing engagement/growth calculations, preparing chart-ready JSON for the frontend, verifying Supabase-issued JWTs locally (no separate Auth Service), and creating in-app notifications / generating on-demand CSV reports (no separate Notification Service - both were folded into this service to make free-tier hosting realistic; see the root README's Services section). It does **not** render charts.

---

## Architecture

Clean, layered architecture following SOLID principles, with constructor-only dependency injection (Lombok `@RequiredArgsConstructor`, never field injection).

```
Controller  →  Service (interface)  →  ServiceImpl  →  Repository  →  Entity
                    ↑                        ↑
                  Mapper (MapStruct)     Client (Feign / Mock)
```

- **controller** — REST endpoints, request/response mapping, Swagger annotations only. No business logic.
- **service / service.impl** — business logic, calculations, orchestration. Interfaces decouple contracts from implementation.
- **repository** — Spring Data JPA repositories with custom aggregate queries.
- **entity** — JPA entities (`SocialAccount`, `Analytics`).
- **dto.request / dto.response** — API contracts, decoupled from entities.
- **mapper** — MapStruct entity ↔ DTO conversion.
- **client** — the generic `SocialMediaClient` abstraction, `MockSocialMediaClient` (simulated data fallback for any platform without a real integration yet), and `SocialMediaClientResolver` (prefers a real client over the mock when both exist for a platform). Platform-specific code does **not** live here — see below.
- **youtube** (and, as they're built, **instagram** / **facebook** / **spotify** / **tiktok**) — each real platform integration is a self-contained top-level package with its own OAuth service, API client (`youtube.api`), external API response DTOs (`youtube.api.dto`), outward-facing DTOs (`youtube.dto`), service/service.impl, controller, and `*Properties` config class. Nothing outside the platform's own package needs to change to add one — see Extensibility below.
- **security** — stateless JWT filter (`JwtAuthenticationFilter`) that verifies Supabase-issued tokens locally via `JwtUtil` (HMAC-SHA256 against `supabase.jwt-secret`, no network call). Also holds `StateTokenService`, the HMAC OAuth-state signer shared by every platform's connect flow.
- **exception** — typed exceptions + `GlobalExceptionHandler` for consistent error responses.
- **util / validator / constant** — calculation engine, shared helpers, enums.
- **scheduler** — hourly automatic synchronization job.
- **config** — Security, CORS, shared OAuth (`OAuthProperties`), Feign, OpenAPI, JPA repository/entity scanning (`com.platform.notification` is a sibling package, not nested under this one), JPA auditing configuration.
- **com.platform.notification** (sibling top-level package, not nested under `com.platform.analytics`) — the former standalone Notification Service: in-app notifications and on-demand CSV reports. Calls into `AnalyticsQueryService` directly (in-process, not Feign) to pull data for a report.

### Extensibility

Adding a new platform (Instagram, Facebook, Spotify, TikTok, or beyond) requires only:
1. A new `Platform` enum constant.
2. A new top-level package (e.g. `com.platform.analytics.instagram`) containing that platform's OAuth service, API client, DTOs, and a `SocialMediaClient` implementation — following the `youtube` package as the reference pattern.
3. Registering the client as a Spring bean (typically gated behind an `<platform>.enabled` property via `@ConditionalOnProperty`, exactly as `YouTubeSocialMediaClient` is).

No controller, service, or repository code outside that new package needs to change — the sync scheduler, calculations, and chart endpoints iterate over `Platform.values()` and any bean implementing `SocialMediaClient`.

---

## Database Design

**`social_accounts`**
| Field | Notes |
|---|---|
| id (UUID, PK) | |
| userId | owning creator |
| platform | enum: YOUTUBE, INSTAGRAM, TIKTOK, FACEBOOK, SPOTIFY |
| accountId | external platform account id |
| accountName, username, profileImage | |
| accessToken, refreshToken | never exposed in API responses |
| connectedAt, lastSynced | |
| active | |

Unique constraint: `(platform, accountId)`.

**`analytics`**
| Field | Notes |
|---|---|
| id (UUID, PK) | |
| socialAccountId (FK) | |
| analyticsDate | one row per account per day |
| followers, following, impressions, reach, profileVisits, views, watchTime | |
| likes, comments, shares, saves, posts | |
| engagementRate | computed on write |
| createdAt | |

Unique constraint: `(socialAccountId, analyticsDate)` — the time-series foundation for trends, growth, and charts.

---

## API Documentation

Full interactive documentation via Swagger UI once running:

- Swagger UI: `http://localhost:8082/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8082/v3/api-docs`

### Account APIs
| Method | Path | Description |
|---|---|---|
| POST | `/api/accounts/connect` | Connect a new social account (triggers initial sync) |
| GET | `/api/accounts` | List all connected accounts |
| GET | `/api/accounts/{id}` | Get one account |
| PUT | `/api/accounts/{id}` | Update mutable account fields |
| DELETE | `/api/accounts/{id}` | Disconnect account |
| GET | `/api/accounts/{id}/sync` | Trigger an on-demand sync |

### Dashboard API
| Method | Path | Description |
|---|---|---|
| GET | `/api/dashboard` | Aggregated KPI dashboard |

### Analytics APIs
| Method | Path |
|---|---|
| GET | `/api/analytics/report` |
| GET | `/api/analytics/trends` |
| GET | `/api/analytics/platform-comparison` |
| GET | `/api/analytics/summary` |
| GET | `/api/analytics/top-platform` |
| GET | `/api/analytics/engagement` |
| GET | `/api/analytics/growth` |

All accept optional `startDate`, `endDate` (ISO `yyyy-MM-dd`) and `platform` query parameters.

### Chart APIs (chart-ready JSON only — no rendering)
| Method | Path | Frontend renders as |
|---|---|---|
| GET | `/api/charts/engagement` | Multi-line chart |
| GET | `/api/charts/followers` | Multi-line chart |
| GET | `/api/charts/views` | Multi-line chart |
| GET | `/api/charts/platform-comparison` | Bar chart |
| GET | `/api/charts/engagement-distribution` | Pie chart |
| GET | `/api/charts/top-content` | Table / cards |
| GET | `/api/charts/audience-demographics` | Age/gender/city/country breakdowns (platforms that support it) |
| GET | `/api/charts/weekly-growth` | Line chart (daily deltas, 7 days) |
| GET | `/api/charts/monthly-growth` | Line chart (monthly deltas, 6 months) |

### Authentication

Every endpoint except `/swagger-ui/**`, `/v3/api-docs/**`, and `/actuator/health/**` requires:

```
Authorization: Bearer <jwt>
```

The token is validated locally (HMAC-SHA256 against `supabase.jwt-secret`, see `JwtUtil`) - no network call. Requests with a missing/invalid token receive a `401` with a structured `ErrorResponse` body.

### Error Response Shape

```json
{
  "timestamp": "2026-07-13T10:15:30",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "VALIDATION_ERROR",
  "message": "Validation failed for one or more fields",
  "path": "/api/accounts/connect",
  "validationErrors": [
    { "field": "accountId", "message": "accountId is required", "rejectedValue": null }
  ]
}
```

---

## Business Logic / Calculations

All formulas live in `util.AnalyticsCalculator` (pure, stateless, fully unit-tested):

- **Engagement Rate** = `(Likes + Comments + Shares) / Followers × 100`
- **Growth Rate (%)** = `(End − Start) / Start × 100`
- **Follower Difference**, **Average Daily Views**, **Average Reach**
- **Platform Ranking** — by engagement rate / followers / views
- **Moving Average** — configurable window (default 7-day)
- **Percentage Increase / Decrease**

---

## Real YouTube Integration (reference implementation)

Every platform starts on `MockSocialMediaClient` (realistic synthetic data). **YouTube** now also has a real, OAuth-backed implementation — `YouTubeSocialMediaClient` — demonstrating the pattern to replicate for Instagram/TikTok/Facebook later.

### How the switch works

- `youtube.enabled=false` (default) → `YouTubeSocialMediaClient` bean is never created → `MockSocialMediaClient` continues to handle YouTube, exactly as before.
- `youtube.enabled=true` + valid Google OAuth credentials → `SocialMediaClientResolver` automatically prefers the real client over the mock for YouTube. No controller, service, or scheduler code changes.

### What's real vs. simulated

| Piece | Status |
|---|---|
| OAuth 2.0 authorization-code flow (`/api/oauth/youtube/authorize`, `/api/oauth/youtube/callback`) | **Real** — redirects to Google's actual consent screen |
| Channel identity (id, title, thumbnail) | **Real** — via YouTube Data API v3 `channels.list` |
| Subscriber count, total channel views, video count | **Real** — via YouTube Data API v3 |
| Top content (views/likes/comments per video) | **Real** — via `search.list` + `videos.list` |
| Token refresh | **Real** — Google OAuth token endpoint, triggered proactively when `tokenExpiresAt` is near |
| Per-day engagement deltas, watch time, "shares", "saves" | **Approximated** — the Data API only exposes cumulative totals and recent-video stats; true daily deltas and watch time require the separate **YouTube Analytics API** (different scope/endpoint), which is a natural next step |

### Setting it up

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project, enable **YouTube Data API v3**, and create an OAuth 2.0 Client ID (type: Web application).
2. Add an authorized redirect URI matching `YOUTUBE_REDIRECT_URI` below (e.g. `http://localhost:8082/api/oauth/youtube/callback`).
3. Set environment variables:

```bash
YOUTUBE_INTEGRATION_ENABLED=true
YOUTUBE_CLIENT_ID=your-client-id.apps.googleusercontent.com
YOUTUBE_CLIENT_SECRET=your-client-secret
YOUTUBE_REDIRECT_URI=http://localhost:8082/api/oauth/youtube/callback
YOUTUBE_FRONTEND_REDIRECT=http://localhost:3000/dashboard
OAUTH_STATE_SECRET=some-long-random-production-secret
```

4. Frontend flow: call `GET /api/oauth/youtube/authorize` (authenticated) → redirect the browser to the returned `authorizationUrl` → after the user approves, Google redirects to the public `/api/oauth/youtube/callback` endpoint → the service exchanges the code, fetches the channel, saves/updates the `SocialAccount`, runs an initial sync, and redirects the browser to `YOUTUBE_FRONTEND_REDIRECT`.

The OAuth `state` parameter is HMAC-signed (`StateTokenService`) rather than relying on server-side sessions, since the callback is a public endpoint that never carries the caller's JWT.

---

## Real Spotify Integration

**Spotify** also has a real, OAuth-backed implementation — `SpotifySocialMediaClient` — but it is a fundamentally different *kind* of integration than YouTube/Instagram/Facebook, and that's worth understanding before connecting an account.

### The hard limitation: Spotify has no public API for artist streaming analytics

Monthly listeners, stream counts, and playlist adds — the numbers an artist actually cares about — live only in the private **Spotify for Artists** dashboard, which has no public API. The closest thing, Spotify's "Provider API," is restricted to labels/distributors with a direct distribution deal with Spotify. Neither is available to a third-party platform like this one, and there is no workaround — this is a Spotify platform restriction, not a gap in this implementation.

What the public **Spotify Web API** *does* provide is different in kind: the connected user's own listening activity — top artists/tracks, recently played, followed artists, and their own profile follower count. So this integration reflects **"what does this connected account listen to,"** not **"how is my audience engaging with my music."**

### What's real vs. approximated vs. not applicable

| `Analytics` field | Status |
|---|---|
| `followers` | **Real** — the connected account's own profile follower count (`GET /me`) |
| `following` | **Real** — count of artists the account follows (`GET /me/following?type=artist`) |
| `views` | **Approximated** — count of items in the last ≤50 recently-played tracks (`GET /me/player/recently-played`); Spotify does not expose full listening history via API, only this rolling window |
| `watchTime` | **Approximated** — summed duration of that same recently-played window, in minutes |
| `impressions`, `reach`, `profileVisits`, `likes`, `comments`, `shares`, `saves`, `posts` | **Always 0** — no Spotify equivalent exists for a personal account. Not fabricated; genuinely absent. |
| Top content (`/api/charts/top-content`) | **Real** — top tracks via `GET /me/top/tracks`, title formatted as `"{track} — {artist}"` |
| Token refresh | **Real** — Spotify Accounts Service token endpoint, triggered proactively when `tokenExpiresAt` is near |

**Caveat on top content:** `TopContentResponse.views` holds Spotify's own 0–100 "popularity" score for Spotify tracks (there is no public per-track play-count endpoint), not a play count. It is **not** comparable in magnitude to a YouTube video's real view count — a Spotify track showing `80` sits next to a YouTube video showing `50000` in any cross-platform top-content comparison. This is intentional and documented rather than hidden, per the platform's explicit choice to reuse the existing field shape.

### Access restrictions (as of Spotify's February 2026 developer platform changes)

- The connecting Spotify account **must have Premium** — Development Mode (the default, no-approval tier) rejects free-tier accounts. This service checks `product` on the fetched profile and rejects non-Premium accounts with a clear `400` before ever attempting a sync.
- Development Mode caps out at **5 authorized users per Client ID**. Scaling beyond that requires Spotify's "Extended Quota Mode," which itself requires being a registered business with a launched service and 250,000+ monthly active users.

### Setting it up

1. In the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard), create an app, check "Web API" under APIs/SDKs.
2. Add a redirect URI matching `SPOTIFY_REDIRECT_URI` below (e.g. `http://localhost:8082/api/oauth/spotify/callback`).
3. Set environment variables:

```bash
SPOTIFY_INTEGRATION_ENABLED=true
SPOTIFY_CLIENT_ID=your-client-id
SPOTIFY_CLIENT_SECRET=your-client-secret
SPOTIFY_REDIRECT_URI=http://localhost:8082/api/oauth/spotify/callback
SPOTIFY_FRONTEND_REDIRECT=http://localhost:3000/dashboard
OAUTH_STATE_SECRET=some-long-random-production-secret
```

4. Frontend flow: call `GET /api/oauth/spotify/authorize` (authenticated) → redirect the browser to the returned `authorizationUrl` → after the user approves, Spotify redirects to the public `/api/oauth/spotify/callback` endpoint → the service exchanges the code (via HTTP Basic auth, unlike Google's form-body approach), fetches the profile, rejects non-Premium accounts, saves/updates the `SocialAccount`, runs an initial sync, and redirects the browser to `SPOTIFY_FRONTEND_REDIRECT`.

`OAUTH_STATE_SECRET` is shared with the YouTube integration — set once, used by every platform's connect flow.

---

## Real Instagram Integration

**Instagram** also has a real, OAuth-backed implementation — `InstagramSocialMediaClient` — using Meta's **Instagram Graph API** via the **"Business Login for Instagram"** flow (`instagram.com/oauth/authorize`, not the older Facebook Login for Business flow). This flow does **not** require the account to be linked to a Facebook Page.

### Hard requirement: Business or Creator account only

The Instagram Graph API only works with Instagram **Business or Creator** professional accounts — personal accounts are not supported and there is no workaround. The account must be converted (free, in the Instagram app settings) before it can be connected here.

### The token model is different from YouTube/Spotify

Instagram has **no separate refresh token**. Instead:

1. Authorization code → short-lived access token (~1 hour, `POST api.instagram.com/oauth/access_token`)
2. Short-lived token → long-lived access token (~60 days, `GET graph.instagram.com/access_token?grant_type=ig_exchange_token`)
3. The long-lived access token is refreshed **in place** (`GET graph.instagram.com/refresh_access_token?grant_type=ig_refresh_token`), valid any time between 24 hours after issuance and its expiry.

`resolveFreshAccessToken` refreshes proactively (7-day buffer before expiry) by calling step 3 directly on the stored access token — there is no `refreshToken` column populated for Instagram accounts.

### What's real vs. approximated vs. not applicable

| `Analytics` field | Status |
|---|---|
| `followers`, `following`, `posts` | **Real** — via the IG User node (`followers_count`, `follows_count`, `media_count`) |
| `reach`, `views`, `likes`, `comments`, `shares`, `saves` | **Real** — account-level insights, `period=day`, `metric_type=total_value` |
| `impressions` | **Always 0** — deprecated by Meta for media created after July 2, 2024, no replacement metric |
| `profileVisits` | **Always 0** — `profile_views` metric fully deprecated as of April 21, 2025 |
| `watchTime` | **Always 0** — no equivalent account-level metric exists |
| Top content (`/api/charts/top-content`) | **Real** — per-media `like_count`, `comments_count`, `view_count`, `shares_count`, `caption`, `permalink`, `thumbnail_url` |
| Audience demographics (`/api/charts/audience-demographics`) | **Real** — follower age/gender/city/country breakdowns via `follower_demographics` insights |
| Token refresh | **Real** — see token model above |

### Audience demographics caveats

- Requires a `timeframe` param, not `period` — only `this_month` and `this_week` are currently valid (older windows like `last_30_days` were deprecated in API v20.0+). This service uses `this_month`.
- Each `breakdown` dimension (`age`, `gender`, `city`, `country`) requires its own API call — Meta does not support combined dimensions in one request. `fetchAudienceDemographics` makes 4 calls per account.
- Meta enforces minimums: **100+ followers** for `follower_demographics`. Accounts below that return an error, which this service catches and logs — the account is simply omitted from the demographics chart rather than failing the whole request.

### Setting it up

1. In [Meta for Developers](https://developers.facebook.com/), create an app and add the **"Instagram"** product with the **"Business Login for Instagram"** flow (not "Facebook Login for Business").
2. Convert the Instagram account you want to connect to a **Business or Creator** account (Instagram app → Settings → Account type).
3. Add yourself (or other test accounts) as an **Instagram tester** under the app's Instagram product settings, and accept the tester invite from the Instagram app — required for Standard (development-mode) API access before App Review.
4. Add a redirect URI matching `INSTAGRAM_REDIRECT_URI` below (e.g. `http://localhost:8082/api/oauth/instagram/callback`).
5. Set environment variables:

```bash
INSTAGRAM_INTEGRATION_ENABLED=true
INSTAGRAM_CLIENT_ID=your-instagram-app-id
INSTAGRAM_CLIENT_SECRET=your-instagram-app-secret
INSTAGRAM_REDIRECT_URI=http://localhost:8082/api/oauth/instagram/callback
INSTAGRAM_FRONTEND_REDIRECT=http://localhost:3000/dashboard
OAUTH_STATE_SECRET=some-long-random-production-secret
```

6. Frontend flow: call `GET /api/oauth/instagram/authorize` (authenticated) → redirect the browser to the returned `authorizationUrl` → after the user approves, Instagram redirects to the public `/api/oauth/instagram/callback` endpoint → the service exchanges the code, walks the short-lived → long-lived token exchange, fetches the profile, saves/updates the `SocialAccount`, runs an initial sync, and redirects the browser to `INSTAGRAM_FRONTEND_REDIRECT`.

Scaling past a handful of testers to real external users requires Meta **App Review** (for the `instagram_business_basic` / `instagram_business_manage_insights` scopes) plus **Business Verification** — standard Meta platform requirements, not specific to this service.

---

## Real TikTok Integration

**TikTok** also has a real, OAuth-backed implementation — `TikTokSocialMediaClient` — using **TikTok Login Kit** (OAuth 2.0) and the **TikTok Display API**. Its token model matches YouTube's (a real, separate refresh token) rather than Instagram's single-token model.

### Token model

- Access token: 24-hour lifetime.
- Refresh token: 365-day lifetime, exchanged at the same `POST /v2/oauth/token/` endpoint with `grant_type=refresh_token`.
- TikTok may rotate the refresh token on every refresh call — per the official docs, "the returned `refresh_token` may be different than the one passed in the payload," so this service always re-stores whichever one comes back rather than assuming it's stable like Google's.

### What's real vs. approximated vs. not applicable

| `Analytics` field | Status |
|---|---|
| `followers`, `following`, `posts` | **Real** — via `GET /v2/user/info/` (`follower_count`, `following_count`, `video_count`) |
| `likes` | **Real** — the profile's cumulative `likes_count`, the one account-level total TikTok's Display API does expose |
| `views`, `comments`, `shares` | **Approximated** — TikTok has no account-level total for any of these, only per-video counts. This service sums `view_count`/`comment_count`/`share_count` across the most recent page of videos (up to 20) via `POST /v2/video/list/` — the same spirit as the Spotify integration's recently-played-window approximation |
| `impressions`, `reach`, `profileVisits`, `watchTime`, `saves` | **Always 0** — no equivalent metric exists anywhere in the Display API, for any account type. Not fabricated; genuinely absent. |
| Top content (`/api/charts/top-content`) | **Real** — per-video `view_count`, `like_count`, `comment_count`, `share_count`, `video_description`/`title`, `cover_image_url`, `share_url` |
| Audience demographics (`/api/charts/audience-demographics`) | **Not applicable** — TikTok only exposes follower demographics via its separate Marketing/Ads ("TikTok for Business") API, which requires a business account and ad spend context, a fundamentally different developer program from Login Kit. `TikTokSocialMediaClient` does not implement `fetchAudienceDemographics`, so it correctly falls back to the interface's empty default — same as Spotify. |
| Token refresh | **Real** — TikTok's OAuth token endpoint, triggered proactively when `tokenExpiresAt` is near |

### Setting it up

1. In the [TikTok for Developers portal](https://developers.tiktok.com/), create an app and add the **Login Kit** product.
2. Request the `user.info.basic`, `user.info.profile`, `user.info.stats`, and `video.list` scopes under the app's Scopes settings (all Standard-tier, no special review needed for development).
3. Add yourself (or other test users) as a registered tester in the app's settings — required for Standard/development-mode API access before App Review.
4. Add a redirect URI matching `TIKTOK_REDIRECT_URI` below (e.g. `http://localhost:8082/api/oauth/tiktok/callback`).
5. Set environment variables:

```bash
TIKTOK_INTEGRATION_ENABLED=true
TIKTOK_CLIENT_KEY=your-tiktok-client-key
TIKTOK_CLIENT_SECRET=your-tiktok-client-secret
TIKTOK_REDIRECT_URI=http://localhost:8082/api/oauth/tiktok/callback
TIKTOK_FRONTEND_REDIRECT=http://localhost:3000/dashboard
OAUTH_STATE_SECRET=some-long-random-production-secret
```

6. Frontend flow: call `GET /api/oauth/tiktok/authorize` (authenticated) → redirect the browser to the returned `authorizationUrl` → after the user approves, TikTok redirects to the public `/api/oauth/tiktok/callback` endpoint → the service exchanges the code, fetches the profile, saves/updates the `SocialAccount`, runs an initial sync, and redirects the browser to `TIKTOK_FRONTEND_REDIRECT`.

Scaling past a handful of testers to real external users requires TikTok **App Review** for the requested scopes — a standard TikTok platform requirement, not specific to this service.

---

## Real Facebook Integration

**Facebook** also has a real, OAuth-backed implementation — `FacebookSocialMediaClient` — using **Facebook Login** and the **Graph API**'s Page endpoints. A connected account here is a **Facebook Page** (not the logging-in person), and its token model is a third, distinct shape from every other platform in this service.

### Hard requirement: the connecting person must manage a Facebook Page

This integration reads Page-level data, so the person completing OAuth must be an admin or editor of at least one Facebook Page. If they manage multiple Pages, only the first one returned by Facebook is connected — the same one-account-per-connect simplification used by the YouTube integration.

### Token model — no refresh needed at all

1. Authorization code → short-lived user access token (~1-2 hours)
2. Short-lived → long-lived user access token (~60 days), via `GET /oauth/access_token?grant_type=fb_exchange_token`
3. Long-lived user token → **Page access token**, via `GET /me/accounts`

Per Meta's own docs, a Page access token obtained this way **does not expire** under normal conditions. So unlike YouTube/TikTok (refresh token pair) or Instagram (single refreshable token), this service does not proactively refresh anything for Facebook — it stores the Page token and uses it as-is. If Facebook ever invalidates it (the user revokes access, changes their password), calls simply start failing and the user has to reconnect; there's no silent recovery path, by design.

### Metric deprecations this integration had to navigate

Meta deprecated large batches of Page Insights metrics in 2025 (`page_fans`, `page_impressions`, and dozens of related `_unique` variants). This service was built directly against the *current* metric set, not the deprecated one:

| Old (deprecated) | Used instead |
|---|---|
| `page_fans` | Page node's `followers_count` field |
| `page_impressions` | `page_media_view` |
| `page_impressions_unique` | `page_total_media_view_unique` |

### What's real vs. approximated vs. not applicable

| `Analytics` field | Status |
|---|---|
| `followers` | **Real** — Page node's `followers_count` |
| `views` | **Real** — `page_media_view` (day) |
| `reach` | **Real** — `page_total_media_view_unique` (day), the replacement for the deprecated unique-impressions metric |
| `likes` | **Real** — summed `page_actions_post_reactions_total` (that day's reactions across all types) |
| `comments`, `shares` | **Approximated** — no account-level total exists, only per-post counts, so this service sums them across the most recent page of posts, same approach as TikTok |
| `impressions` | **Always 0** — the term itself was retired in favor of `page_media_view`, already reported under `views`; duplicating it would be misleading |
| `profileVisits`, `watchTime`, `saves` | **Always 0** — no confirmed Facebook Page equivalent for any of these |
| Top content (`/api/charts/top-content`) | **Real** — real per-post `post_media_view` view counts (one extra Graph API call per candidate post, capped at 15 candidates), plus real likes/comments/shares |
| Audience demographics (`/api/charts/audience-demographics`) | **Partial** — city/country breakdowns are real (`page_follows_city`/`page_follows_country`); age/gender are **not available at all**, since Meta blocks Page audience age/gender data for any app connection made after March 14, 2024, which includes this integration regardless of when you enable it |
| Token refresh | **Not applicable** — see token model above |

### Setting it up

1. In the [Meta for Developers portal](https://developers.facebook.com/), create an app and add the **Facebook Login** product.
2. Request the `pages_show_list`, `pages_read_engagement`, `pages_read_user_content`, and `read_insights` permissions under the app's Permissions settings (Standard Access; `read_insights` requires the first two as dependencies).
3. Add yourself (or other testers) as an app admin/editor, and make sure that person is also an admin of at least one real Facebook Page — required for Standard/development-mode access before App Review.
4. Add a redirect URI matching `FACEBOOK_REDIRECT_URI` below (e.g. `http://localhost:8082/api/oauth/facebook/callback`).
5. Set environment variables:

```bash
FACEBOOK_INTEGRATION_ENABLED=true
FACEBOOK_APP_ID=your-facebook-app-id
FACEBOOK_APP_SECRET=your-facebook-app-secret
FACEBOOK_REDIRECT_URI=http://localhost:8082/api/oauth/facebook/callback
FACEBOOK_FRONTEND_REDIRECT=http://localhost:3000/dashboard
OAUTH_STATE_SECRET=some-long-random-production-secret
```

6. Frontend flow: call `GET /api/oauth/facebook/authorize` (authenticated) → redirect the browser to the returned `authorizationUrl` → after the user approves, Facebook redirects to the public `/api/oauth/facebook/callback` endpoint → the service exchanges the code, walks the user-token-to-Page-token exchange, fetches the Page's identity, saves/updates the `SocialAccount`, runs an initial sync, and redirects the browser to `FACEBOOK_FRONTEND_REDIRECT`.

Scaling past a handful of testers to real external users requires Meta **App Review** for the requested permissions — a standard Meta platform requirement, not specific to this service.

---



### Prerequisites
- Java 21
- Maven 3.9+
- PostgreSQL 16 (or Docker)

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | 8082 | HTTP port |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | localhost / 5432 / analytics_db | PostgreSQL connection |
| `DB_USERNAME` / `DB_PASSWORD` | postgres / postgres | PostgreSQL credentials |
| `SUPABASE_JWT_SECRET` | dev-only-placeholder | HMAC secret verifying Supabase-issued JWTs locally — **must be a real value from your Supabase project in production** |
| `SYNC_ENABLED` | true | Enable/disable the hourly scheduler |
| `SYNC_CRON` | `0 0 * * * *` | Cron expression for sync frequency |
| `MOCK_DATA_ENABLED` | true | Use the mock social media client |
| `YOUTUBE_INTEGRATION_ENABLED` | false | Enable the real YouTube Data API / OAuth integration |
| `YOUTUBE_CLIENT_ID` / `YOUTUBE_CLIENT_SECRET` | — | Google OAuth 2.0 credentials |
| `YOUTUBE_REDIRECT_URI` | http://localhost:8082/api/oauth/youtube/callback | Must match the Google Cloud Console redirect URI |
| `YOUTUBE_FRONTEND_REDIRECT` | http://localhost:3000/dashboard | Where the browser lands after a successful connect |
| `SPOTIFY_INTEGRATION_ENABLED` | false | Enable the real Spotify Web API / OAuth integration |
| `SPOTIFY_CLIENT_ID` / `SPOTIFY_CLIENT_SECRET` | — | Spotify OAuth 2.0 credentials |
| `SPOTIFY_REDIRECT_URI` | http://localhost:8082/api/oauth/spotify/callback | Must match the Spotify Developer Dashboard redirect URI |
| `SPOTIFY_FRONTEND_REDIRECT` | http://localhost:3000/dashboard | Where the browser lands after a successful connect |
| `INSTAGRAM_INTEGRATION_ENABLED` | false | Enable the real Instagram Graph API / OAuth integration |
| `INSTAGRAM_CLIENT_ID` / `INSTAGRAM_CLIENT_SECRET` | — | Meta app ID/secret ("Business Login for Instagram" product) |
| `INSTAGRAM_REDIRECT_URI` | http://localhost:8082/api/oauth/instagram/callback | Must match the Meta app's configured redirect URI |
| `INSTAGRAM_FRONTEND_REDIRECT` | http://localhost:3000/dashboard | Where the browser lands after a successful connect |
| `TIKTOK_INTEGRATION_ENABLED` | false | Enable the real TikTok Display API / OAuth integration |
| `TIKTOK_CLIENT_KEY` / `TIKTOK_CLIENT_SECRET` | — | TikTok Login Kit client key/secret |
| `TIKTOK_REDIRECT_URI` | http://localhost:8082/api/oauth/tiktok/callback | Must match the TikTok Developer app's configured redirect URI |
| `TIKTOK_FRONTEND_REDIRECT` | http://localhost:3000/dashboard | Where the browser lands after a successful connect |
| `FACEBOOK_INTEGRATION_ENABLED` | false | Enable the real Facebook Graph API / OAuth integration |
| `FACEBOOK_APP_ID` / `FACEBOOK_APP_SECRET` | — | Meta app ID/secret (Facebook Login product) |
| `FACEBOOK_REDIRECT_URI` | http://localhost:8082/api/oauth/facebook/callback | Must match the Meta app's configured redirect URI |
| `FACEBOOK_FRONTEND_REDIRECT` | http://localhost:3000/dashboard | Where the browser lands after a successful connect |
| `OAUTH_STATE_SECRET` | dev-only-change-me-in-production | HMAC secret signing the OAuth `state` parameter for every platform's connect flow — **set a strong value in production** |

### Run Locally

```bash
# 1. Start PostgreSQL (or use docker-compose, see below)
# 2. Build and run
mvn clean install
mvn spring-boot:run
```

The service starts on `http://localhost:8082`.

### Run with Docker

This service is one part of the unified platform (this service + Postgres)
— see the root [README.md](../../README.md) for the actual
`docker-compose.yml` (at the repo root, not here) and the `.env` setup it
requires.

---

## Testing

```bash
mvn test
```

Includes:
- **Unit tests** — `AnalyticsCalculatorTest` (all formulas), `StateTokenServiceTest` (OAuth state sign/verify)
- **Service tests (Mockito)** — `SocialAccountServiceImplTest`, `AnalyticsSyncServiceImplTest`, `SocialMediaClientResolverTest`, `NotificationServiceImplTest`, `ReportServiceImplTest`
- **Repository tests (`@DataJpaTest`, H2)** — `AnalyticsRepositoryTest`
- **Controller tests (`@WebMvcTest`)** — `AccountControllerTest`
- **Full end-to-end tests (`@SpringBootTest` + MockMvc, real datasource)** — `DashboardIntegrationTest`, which specifically guards against `LazyInitializationException` on the dashboard/chart endpoints
- **Controller tests (MockMvc)** — `AccountControllerTest`
- **Context load smoke test** — `AnalyticsServiceApplicationTests`

---

## Logging

SLF4J throughout. Logged events include:
- Incoming requests (controller layer, `log.info`)
- Errors (`GlobalExceptionHandler`, `log.error`/`log.warn`)
- Synchronization events (`AnalyticsSyncServiceImpl`, `AnalyticsSyncScheduler`)
- Analytics calculations (debug-level in `MockSocialMediaClient`)

---

## Future Improvements

- Replace `MockSocialMediaClient` with real Instagram Graph API / TikTok API / Facebook Graph API integrations, following the `YouTubeSocialMediaClient` pattern.
- Integrate the YouTube Analytics API (separate from the Data API used today) for true daily deltas, watch time, and audience demographics instead of the current cumulative-totals approximation.
- Add Redis caching for dashboard/report endpoints.
- Add pagination to `/api/analytics/trends` for very large date ranges.
- Publish domain events (e.g. `AccountConnectedEvent`, `SyncCompletedEvent`) to a message broker for other microservices to consume.
- Add rate limiting per user on sync endpoints.
- Replace the in-request HMAC-signed OAuth `state` with a server-side store (e.g. Redis) if scaling to multiple instances behind a load balancer without sticky sessions.

---

## Tech Stack

Java 21 · Spring Boot 3.3 · Maven · PostgreSQL · Spring Data JPA · Spring Validation · Spring Security · JJWT · Lombok · OpenAPI/Swagger · Docker · JUnit 5 · Mockito · MapStruct · OpenFeign
