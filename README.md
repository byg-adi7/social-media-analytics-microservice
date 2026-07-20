# Social Media Analytics Microservice

[![CI](https://github.com/byg-adi7/social-media-analytics-microservice/actions/workflows/ci.yml/badge.svg)](https://github.com/byg-adi7/social-media-analytics-microservice/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/github/license/byg-adi7/social-media-analytics-microservice)](LICENSE)
![Java](https://img.shields.io/badge/Java-17%20%7C%2021-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?logo=springboot&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-blue?logo=docker&logoColor=white)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/byg-adi7/social-media-analytics-microservice/pulls)

A microservices platform for connecting social media accounts (YouTube,
Instagram, TikTok, Facebook, Spotify), syncing their analytics, and
surfacing dashboards, reports, and notifications.

**Author:** [Adi Ransford](https://github.com/byg-adi7)

**License:** [MIT](LICENSE)

## Services

| Service | Port | What it does |
|---|---|---|
| `backend/auth-service` | 8001 | Registration, login, JWT issuance. Every other service delegates authentication here — none of them validate tokens locally. |
| `backend/analytics-service` | 8002 | Connects social accounts, syncs their analytics (real integrations for all 5 platforms, falling back to mock data by default), and serves dashboards/charts/reports. See [its README](backend/analytics-service/README.md) for full API docs and per-platform integration setup. |
| `backend/notification-service` | 8003 | In-app notifications (fired automatically on account-connected/sync-failure events) and on-demand CSV reports pulled from the Analytics Service. |
| `api-gateway` (nginx) | 8080 | Single public entry point, routes `/api/*` to the right service. |
| `postgres` | 5432 | Shared Postgres instance — each service owns its own tables, managed by its own versioned Flyway migrations (`backend/*/src/main/resources/db/migration`), not a shared script. |

**Building the frontend or want the full API reference?** See
[`FRONTEND_INTEGRATION_GUIDE.md`](FRONTEND_INTEGRATION_GUIDE.md) — backend
readiness status, every endpoint, request/response shapes, and the
authentication flow.

## Quickstart (Docker)

1. **Set up your secrets** — copy the template and fill in real values:
   ```bash
   cp .env.example .env
   ```
   Generate a strong value for each `*_SECRET`/`*_KEY`/`*_PASSWORD` in `.env`, e.g.:
   ```bash
   openssl rand -base64 32
   ```
   `.env` is gitignored — it must never be committed. `docker-compose.yml`
   reads every secret from it and will refuse to start with a clear error
   if one is missing.

2. **Start everything:**
   ```bash
   docker compose up --build
   ```
   The gateway is then at `http://localhost:8080`. Without any platform
   credentials configured, every social integration runs on mock data —
   the stack is fully usable out of the box.

3. **(Optional) Enable a real platform integration** — each of the 5
   platforms needs its own developer app registered on that platform's own
   console (redirect URIs, scopes, credentials) before it does anything
   beyond mock data. This is real external setup, not a config flag:
   - **YouTube**: Google Cloud Console OAuth 2.0 client.
   - **Spotify**: Spotify Developer Dashboard app.
   - **Instagram / Facebook**: a Meta Developer app. Both additionally
     require Meta **App Review** (and, for Instagram, Business
     Verification) before real users beyond your own app's
     admins/testers can connect — a Meta platform requirement, not
     something this codebase can bypass.
   - **TikTok**: a TikTok Developer app, also requiring TikTok's own App
     Review to scale past testers.

   Exact steps, required scopes, and env var names for each are in
   [`backend/analytics-service/README.md`](backend/analytics-service/README.md)
   under "Real \<Platform\> Integration". Once you have credentials, set
   the corresponding `*_INTEGRATION_ENABLED=true` and `*_CLIENT_ID`/
   `*_CLIENT_SECRET` (or equivalent) values in `.env`.

## Known gaps

- No frontend exists in this repository.
- The `subscriptions`/billing feature referenced in `database/schema.sql`
  (a deprecated, no-longer-applied seed script — see its header comment)
  was never implemented by any service.
