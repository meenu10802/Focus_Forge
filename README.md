# FocusForge ⚔️

An event-driven study RPG and "Dopamine Bank". Generative AI breaks overwhelming goals into 10-minute micro-quests. Each completed step mints XP, restores HP, and earns Focus Coins you can spend in a real-life reward store.

Built with Spring Boot microservices (Eureka, Spring Cloud Gateway, Kafka, PostgreSQL, Redis) and a React + TypeScript HUD.

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Foundation: Maven multi-module, infra compose, Eureka, gateway, event contracts, frontend shell | ✅ |
| 1 | Identity, JWT auth, live HUD | ⏳ |
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
discovery-server/          :8761  Eureka registry
api-gateway/               :8080  Spring Cloud Gateway (routing, CORS; JWT in Phase 1)
frontend/                  :5173  Vite + React + TS + Tailwind + TanStack Query
infra/docker-compose.yml   Postgres :5432, Redis :6380, Kafka :9092, Kafka UI :8090, Zipkin :9411
```

## Running locally

Prerequisites: JDK 21+, Maven 3.9+, Node 20+ and Docker Desktop.

```bash
# 1. Infrastructure
cd infra && docker compose up -d && cd ..

# 2. Build all modules
mvn clean install

# 3. Services (separate terminals, registry first)
java -jar discovery-server/target/discovery-server-0.1.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar

# 4. Frontend
cd frontend && npm install && npm run dev
```

Check that everything is up:
- http://localhost:8761: Eureka lists `API-GATEWAY`.
- http://localhost:8080/api/health returns `{"status":"UP"}`.
- http://localhost:8080/actuator/gateway/routes lists the configured routes.
- http://localhost:5173: the HUD loads and the gateway pill reads "online".
- http://localhost:8090 opens Kafka UI.
- http://localhost:9411 opens Zipkin.

Routes for services that haven't been built yet return **503** until those services register with Eureka.

## Conventions

- **Database per service**: `identity_db`, `bank_db` and `quest_db` are created by `infra/postgres/init.sql`.
- **Auth at the edge**: the gateway validates the JWT and forwards `X-User-Id` and `X-User-Roles` headers. `/internal/**` is never routed.
- **Events**: topics are defined in `com.focusforge.events.Topics`. The Kafka key is always `userId`, and every event carries an `eventId` that consumers use to deduplicate.
- **Config**: see `.env.example`. Secrets are never committed.
