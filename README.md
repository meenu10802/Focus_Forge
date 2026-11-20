# FocusForge ⚔️

An event-driven study RPG and "Dopamine Bank". Generative AI breaks overwhelming goals into 10-minute micro-quests. Each completed step mints XP, restores HP, and earns Focus Coins you can spend in a real-life reward store.

Built with Spring Boot microservices (Eureka, Spring Cloud Gateway, Kafka, PostgreSQL, Redis) and a React + TypeScript HUD.

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Foundation: Maven multi-module, infra compose, Eureka, gateway, event contracts, frontend shell | ✅ |
| 1 | Identity, JWT auth at the gateway, live HUD | ✅ |
| 2 | AI quest generation (Groq / Gemini, circuit breaker) | ⏳ |
| 3 | Focus loop: idempotent completion, outbox, Kafka fan-out | ⏳ |
| 4 | Reward store with optimistic locking | ⏳ |
| 5 | Live notifications over SSE | ⏳ |
| 6 | Panic button, streaks, leaderboard, heatmap | ⏳ |
| 7 | Tracing, aggregated Swagger, rate limiting, containers, CI | ⏳ |

## Layout

```
pom.xml                    parent POM (Spring Boot 3.3, Spring Cloud 2023.0, Java 21)
common/focus-events/       shared Kafka event records + topic names
common/focus-web/          shared servlet plumbing: @CurrentUser, RFC 7807 errors
discovery-server/          :8761  Eureka registry
api-gateway/               :8080  Spring Cloud Gateway: routing, CORS, JWT validation, RBAC
identity-rpg-service/      :8081  register/login, player profile (HP/XP/level), Redis dashboard cache
frontend/                  :5173  Vite + React + TS + Tailwind + TanStack Query
infra/docker-compose.yml   Postgres :5442, Redis :6380, Kafka :9092, Kafka UI :8090, Zipkin :9411
```

The host ports for Postgres (5442) and Redis (6380) are non-default on purpose, so they don't clash with other local installs. Override them with `POSTGRES_PORT` and `REDIS_PORT`.

## Running locally

Prerequisites: JDK 21+, Maven 3.9+, Node 20+ and Docker Desktop (Docker is also needed for the Testcontainers tests).

```bash
# 1. Infrastructure
cd infra && docker compose up -d && cd ..

# 2. Build + test all modules
mvn clean install

# 3. Services (separate terminals, registry first)
java -jar discovery-server/target/discovery-server-0.1.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar
java -jar identity-rpg-service/target/identity-rpg-service-0.1.0-SNAPSHOT.jar

# 4. Frontend
cd frontend && npm install && npm run dev
```

Open http://localhost:5173, create a hero, and the HUD shows level 1 with 100/100 HP.

To get an admin account, start identity-rpg-service with `FOCUS_ADMIN_EMAILS=you@example.com`. Registering with that email gives you the `ADMIN` role.

### Smoke test with curl

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"me@focus.dev","password":"a-good-password","displayName":"Me"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
curl -s localhost:8080/api/profile/me -H "Authorization: Bearer $TOKEN"
```

### Other endpoints to check

- http://localhost:8761: Eureka lists the running services.
- http://localhost:8080/api/health: gateway health.
- http://localhost:8080/actuator/gateway/routes: the configured routes.
- http://localhost:8090: Kafka UI.
- http://localhost:9411: Zipkin.

Routes for services that aren't running return **503** until those services register with Eureka.

## API (so far)

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | public | `{email, password(8-72), displayName}` → 201 `{accessToken, expiresIn, user}`; 409 if the email is taken |
| POST | `/api/auth/login` | public | 401 with the same message for an unknown email or a wrong password |
| GET | `/api/profile/me` | Bearer | HUD data, cached in Redis per player per day |

Every error comes back as RFC 7807 `application/problem+json`. Validation errors also include an `errors: {field: message}` map.

## Design notes

- **Auth at the edge.** identity-rpg-service signs an HS256 JWT. The gateway checks the signature, issuer and expiry, removes any `X-User-Id` / `X-User-Roles` headers the client sent, and sets its own. Downstream services read the player id with `@CurrentUser UUID userId`. `/api/admin/**` requires `ADMIN` (otherwise 403). `/internal/**` is never routed.
- **No user enumeration.** Login runs a BCrypt comparison even when the email doesn't exist, so unknown and existing emails take about the same time and return the same error.
- **Lazy daily HP reset.** There's no cron job. The first profile read of a new day refills HP. The day is calculated in the timezone set by `FOCUS_TZ`.
- **Cache key includes the date.** The cache key is `identity:dashboard::{userId}:{date}`, so the first read of a new day always misses the cache and applies the HP reset. Writers (Kafka consumers in Phase 3) evict the current day's key.
- **Leveling.** Advancing from level n to n+1 costs `round(100 * n^1.5)` XP: 100, 283, 520, 800 and so on. This lives in `LevelingPolicy`, a pure class with its own unit tests.
- **Database per service.** `identity_db`, `bank_db` and `quest_db` are created by `infra/postgres/init.sql`. Each service runs its own schema migrations with Flyway.
- **Events.** Topics are defined in `com.focusforge.events.Topics`. The Kafka key is always `userId`, and every event carries an `eventId` that consumers use to deduplicate.
- **Config.** See `.env.example`. Secrets are never committed. The dev `JWT_SECRET` default must be overridden anywhere outside your own machine.
