# Analytics Service

Analytics microservice for the **Audience Insights Platform** — a multi-service SaaS product that lets content creators connect multiple social media accounts and view unified analytics from one dashboard.

This service owns **everything analytics-related**: connecting accounts, synchronizing metrics, aggregating data, computing engagement/growth calculations, and preparing chart-ready JSON for the frontend. It does **not** render charts, and it does **not** perform local authentication — every request's JWT is validated against the central Auth Service.

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
- **client** — `SocialMediaClient` abstraction + `MockSocialMediaClient` (swap for real platform APIs later) and `AuthServiceClient` (Feign, JWT validation).
- **security** — stateless JWT filter that delegates validation to the Auth Service; never parses/verifies tokens locally.
- **exception** — typed exceptions + `GlobalExceptionHandler` for consistent error responses.
- **util / validator / constant** — calculation engine, shared helpers, enums.
- **scheduler** — hourly automatic synchronization job.
- **config** — Security, Feign, OpenAPI, JPA auditing configuration.

### Extensibility

Adding a new platform (X, LinkedIn, Twitch) requires only:
1. A new `Platform` enum constant.
2. A new `SocialMediaClient` implementation (or extend the mock) registered as a Spring bean.

No controller, service, or repository code needs to change — the sync scheduler, calculations, and chart endpoints iterate over `Platform.values()` and any bean implementing `SocialMediaClient`.

---

## Database Design

**`social_accounts`**
| Field | Notes |
|---|---|
| id (UUID, PK) | |
| userId | owning creator |
| platform | enum: YOUTUBE, INSTAGRAM, TIKTOK, FACEBOOK |
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
| GET | `/api/charts/weekly-growth` | Line chart (daily deltas, 7 days) |
| GET | `/api/charts/monthly-growth` | Line chart (monthly deltas, 6 months) |

### Authentication

Every endpoint except `/swagger-ui/**`, `/v3/api-docs/**`, and `/actuator/health/**` requires:

```
Authorization: Bearer <jwt>
```

The token is **not** validated locally — it's forwarded to the Auth Service (`auth-service.url` in `application.yml`) via OpenFeign. Requests with a missing/invalid token receive a `401` with a structured `ErrorResponse` body.

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
YOUTUBE_STATE_SECRET=some-long-random-production-secret
```

4. Frontend flow: call `GET /api/oauth/youtube/authorize` (authenticated) → redirect the browser to the returned `authorizationUrl` → after the user approves, Google redirects to the public `/api/oauth/youtube/callback` endpoint → the service exchanges the code, fetches the channel, saves/updates the `SocialAccount`, runs an initial sync, and redirects the browser to `YOUTUBE_FRONTEND_REDIRECT`.

The OAuth `state` parameter is HMAC-signed (`StateTokenService`) rather than relying on server-side sessions, since the callback is a public endpoint that never carries the caller's JWT.

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
| `DDL_AUTO` | update | Hibernate schema strategy |
| `AUTH_SERVICE_URL` | http://localhost:8081 | Base URL of the Auth Service |
| `SYNC_ENABLED` | true | Enable/disable the hourly scheduler |
| `SYNC_CRON` | `0 0 * * * *` | Cron expression for sync frequency |
| `MOCK_DATA_ENABLED` | true | Use the mock social media client |
| `YOUTUBE_INTEGRATION_ENABLED` | false | Enable the real YouTube Data API / OAuth integration |
| `YOUTUBE_CLIENT_ID` / `YOUTUBE_CLIENT_SECRET` | — | Google OAuth 2.0 credentials |
| `YOUTUBE_REDIRECT_URI` | http://localhost:8082/api/oauth/youtube/callback | Must match the Google Cloud Console redirect URI |
| `YOUTUBE_FRONTEND_REDIRECT` | http://localhost:3000/dashboard | Where the browser lands after a successful connect |
| `YOUTUBE_STATE_SECRET` | dev-only-change-me-in-production | HMAC secret signing the OAuth `state` parameter — **set a strong value in production** |

### Run Locally

```bash
# 1. Start PostgreSQL (or use docker-compose, see below)
# 2. Build and run
mvn clean install
mvn spring-boot:run
```

The service starts on `http://localhost:8082`.

### Run with Docker

```bash
# Build and start Analytics Service + PostgreSQL
docker network create platform-network   # once, so other microservices can join
docker-compose up --build
```

---

## Testing

```bash
mvn test
```

Includes:
- **Unit tests** — `AnalyticsCalculatorTest` (all formulas), `StateTokenServiceTest` (OAuth state sign/verify)
- **Service tests (Mockito)** — `SocialAccountServiceImplTest`, `AnalyticsSyncServiceImplTest`, `SocialMediaClientResolverTest`
- **Repository tests (`@DataJpaTest`, H2)** — `AnalyticsRepositoryTest`
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

Java 21 · Spring Boot 3.3 · Maven · PostgreSQL · Spring Data JPA · Spring Validation · Spring Security · JWT (delegated) · Lombok · OpenAPI/Swagger · Docker · JUnit 5 · Mockito · MapStruct · OpenFeign
