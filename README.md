# FocusForge ⚔️

An event-driven study RPG and "Dopamine Bank". Generative AI breaks overwhelming goals into 10-minute micro-quests. Each completed step mints XP, restores HP, and earns Focus Coins you can spend in a real-life reward store.

Built with Spring Boot microservices (Eureka, Spring Cloud Gateway, Kafka, PostgreSQL, Redis) and a React + TypeScript HUD.

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Foundation: Maven multi-module, infra compose, Eureka, gateway, event contracts, frontend shell | ✅ |
| 1 | Identity, JWT auth at the gateway, live HUD | ✅ |
| 2 | AI quest generation (Groq / Gemini, retry + circuit breaker + template fallback) | ✅ |
| 3 | Focus loop: timer, idempotent completion, transactional outbox, Kafka fan-out, coin wallet | ✅ |
| 4 | Reward store with optimistic locking | ⏳ |
| 5 | Live notifications over SSE | ⏳ |
| 6 | Panic button, streaks, leaderboard, heatmap | ⏳ |
| 7 | Tracing, aggregated Swagger, rate limiting, containers, CI | ⏳ |

## Layout

```
pom.xml                    parent POM (Spring Boot 3.3, Spring Cloud 2023.0, Java 21)
common/focus-events/       shared Kafka event records + topic names
common/focus-web/          shared servlet plumbing: @CurrentUser, RFC 7807 errors
common/focus-kafka/        shared consumer error policy: retry x3, then <topic>.DLT
discovery-server/          :8761  Eureka registry
api-gateway/               :8080  Spring Cloud Gateway: routing, CORS, JWT validation, RBAC
identity-rpg-service/      :8081  register/login, player profile (HP/XP/level), Redis dashboard cache
ai-quest-service/          :8082  stateless AI planner (Groq/Gemini), Resilience4j retry + circuit breaker
bank-store-service/        :8083  Focus Coin wallet + append-only ledger (Kafka consumer)
quest-engine-service/      :8084  quests, focus sessions, idempotent completion, transactional outbox -> Kafka
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
GROQ_API_KEY=gsk_... java -jar ai-quest-service/target/ai-quest-service-0.1.0-SNAPSHOT.jar
java -jar quest-engine-service/target/quest-engine-service-0.1.0-SNAPSHOT.jar
java -jar bank-store-service/target/bank-store-service-0.1.0-SNAPSHOT.jar

# 4. Frontend
cd frontend && npm install && npm run dev
```

Open http://localhost:5173 and create a hero. The HUD shows level 1 with 100/100 HP. Click **+ New quest**, describe a goal, and you get 5–8 micro-tasks. Click **▶ Focus** on a task to start its timer, then **Complete & claim loot**. Coins, XP and HP land in the HUD a moment later through Kafka. Abandoning a session costs 10 HP.

**AI keys are optional.** Without `GROQ_API_KEY` (free at console.groq.com), the planner serves a built-in 5-step study template, and the quest card shows an "Offline template" badge. To use Gemini instead, set `FOCUS_AI_PROVIDER=gemini` and `GEMINI_API_KEY`.

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
| POST | `/api/quests/generate` | Bearer | `{goal(3-300)}` → 201 quest with tasks and rewards; 503 if ai-quest-service is down |
| GET | `/api/quests` | Bearer | Your 20 most recent quests, newest first |
| GET | `/api/quests/{id}` | Bearer | 404 for another player's quest |
| POST | `/api/quests/tasks/{id}/start` | Bearer | Starts a focus session. Calling it again returns the same session; 409 if another task is running |
| GET | `/api/quests/sessions/active` | Bearer | 200 with the running session, or 204 if there isn't one |
| POST | `/api/quests/tasks/{id}/complete` | Bearer + `Idempotency-Key` | 200 with the rewards. Same key → the stored response is replayed (`Idempotent-Replayed: true`); 409 if the task is already done; 422 if the key was used for a different task |
| POST | `/api/quests/tasks/{id}/abandon` | Bearer | −10 HP; the task can be started again later |
| GET | `/api/wallet/me` | Bearer | `{balance, updatedAt}` |
| GET | `/api/wallet/me/transactions?page&size` | Bearer | Ledger entries, newest first |

Internal only (the gateway never routes these): `POST /internal/ai/questline` and `POST /internal/ai/split` on ai-quest-service. Its circuit-breaker state is at `localhost:8082/actuator/health`.

Every error comes back as RFC 7807 `application/problem+json`. Validation errors also include an `errors: {field: message}` map.

## Design notes

- **Auth at the edge.** identity-rpg-service signs an HS256 JWT. The gateway checks the signature, issuer and expiry, removes any `X-User-Id` / `X-User-Roles` headers the client sent, and sets its own. Downstream services read the player id with `@CurrentUser UUID userId`. `/api/admin/**` requires `ADMIN` (otherwise 403). `/internal/**` is never routed.
- **No user enumeration.** Login runs a BCrypt comparison even when the email doesn't exist, so unknown and existing emails take about the same time and return the same error.
- **Lazy daily HP reset.** There's no cron job. The first profile read of a new day refills HP. The day is calculated in the timezone set by `FOCUS_TZ`.
- **Cache key includes the date.** The cache key is `identity:dashboard::{userId}:{date}`, so the first read of a new day always misses the cache and applies the HP reset. Writers (Kafka consumers in Phase 3) evict the current day's key.
- **Leveling.** Advancing from level n to n+1 costs `round(100 * n^1.5)` XP: 100, 283, 520, 800 and so on. This lives in `LevelingPolicy`, a pure class with its own unit tests.
- **AI resilience.** Each AI call passes through two layers: `@Retry` (2 attempts) on the outside and `@CircuitBreaker` on the inside. The fallback is attached to the outer `@Retry`. If it were on `@CircuitBreaker`, the breaker would swallow the first failure and the retry would never run. When the breaker is open, requests skip the AI and get the template in about 0 ms instead of waiting out a timeout. A missing API key counts as a configuration problem, not an outage, so it doesn't push the breaker toward opening.
- **AI output is untrusted.**
  - The goal text is wrapped in delimiter tags, and the model is told to treat it as data.
  - The response is parsed tolerantly (code fences, wrong-case values) but strictly checked: it must contain enough tasks, and minutes are clamped to range.
  - **Coins, XP and HP are never taken from the model.** quest-engine works them out from difficulty: MICRO 5/10/2, EASY 10/25/5, MEDIUM 20/50/10, HARD 35/80/15. A prompt-injected "9999 coins" therefore does nothing.
- **No database connection held during the AI call.** quest-engine calls the AI first and only then opens a transaction to save the quest. Holding a connection through a 10-second model call would exhaust the connection pool.

### What happens when you click "Complete"

```
browser ──POST /complete (Idempotency-Key: K)──▶ gateway (JWT ✓, X-User-Id) ──▶ quest-engine
  quest-engine:
    1. Redis  SET idem:{user}:{K} PROCESSING NX EX 60       (seen before? replay / 409 / 422)
    2. one DB transaction:
         task → COMPLETED                                   (@Version: a parallel request fails)
         session → COMPLETED with focus minutes
         INSERT outbox_events(TaskCompletedEvent)           (same transaction: never lost, never phantom)
    3. Redis  SET idem:{user}:{K} DONE + response  EX 24h
  OutboxRelay (every 500 ms, FOR UPDATE SKIP LOCKED) ──▶ Kafka quest.task-completed (key = userId)
        ├─▶ bank-store-service   processed_events dedup → wallet += coins, ledger CREDIT
        └─▶ identity-rpg-service processed_events dedup → +XP / +HP / level-up → evict dashboard cache
                                                             └─▶ rpg.level-up (after commit)
```

- **Exactly-once effect from at-least-once delivery.** The relay and Kafka can deliver an event twice. Each consumer inserts the event's `eventId` into `processed_events` (`ON CONFLICT DO NOTHING`) in the same transaction as the balance or XP change. A redelivered event therefore changes nothing.
- **Two layers of idempotency.**
  - The Redis key gives a client that retries an identical replayed response.
  - The real guarantee is the database state change: a task can only move to COMPLETED once, and `@Version` makes a racing second request fail.
  - If Redis is down, the service keeps working without it ("fails open") and still credits correctly.
- **A bad message can't block the queue.**
  - Consumers retry 3 times, 500 ms apart, then move the record to `<topic>.DLT`.
  - A message that can't even be decoded goes straight to the DLT as raw bytes, via `ErrorHandlingDeserializer`.
- **One focus session at a time.** A partial unique index (`WHERE status = 'ACTIVE'`) makes the database reject a second simultaneous start.
- **Ledger.**
  - Ledger rows are append-only, and the database rejects a negative balance with a `CHECK`.
  - The signed sum of a user's ledger always equals their wallet balance; `LedgerRepository.ledgerBalance` computes it so the two can be compared.
- **Which events use the outbox.** Events that move coins or XP go through the transactional outbox. `LevelUpEvent` only triggers a celebration, so identity sends it after commit on a best-effort basis.
- **Database per service.** `identity_db`, `bank_db` and `quest_db` are created by `infra/postgres/init.sql`. Each service runs its own schema migrations with Flyway.
- **Events.** Topics are defined in `com.focusforge.events.Topics`. The Kafka key is always `userId`, and every event carries an `eventId` that consumers use to deduplicate.
- **Config.** See `.env.example`. Secrets are never committed. The dev `JWT_SECRET` default must be overridden anywhere outside your own machine.
