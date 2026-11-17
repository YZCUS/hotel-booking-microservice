# Hotel Booking Microservices

A demo-ready hotel reservation platform built with Spring Boot 3, Java 21, RabbitMQ, Redis, PostgreSQL, Meilisearch, and a Vite React frontend.

## Architecture

```text
React demo UI (3000)
        |
API Gateway (8080): JWT, CORS, rate limiting, routing
        |
        +--> User Service (8081): registration, login, profiles
        +--> Hotel Service (8082): hotels, rooms, favorites
        +--> Booking Service (8083): reservations, inventory, pricing
        +--> Search Service (8084): Meilisearch indexing/search
        +--> Notification Service (8085): email notifications

PostgreSQL schemas: user_svc, hotel_svc, booking_svc, search_svc
Redis: cache and rate-limit state
RabbitMQ: booking, hotel, and user domain events
Prometheus/Grafana: metrics and dashboards
```

Local development uses one PostgreSQL instance with service-owned schemas for speed. The production direction is database-per-service or independently managed schemas with API/event-only integration.

## Features

- Browse seeded hotels and room types.
- Register or log in with JWT authentication.
- Create and cancel bookings through the API Gateway.
- Reserve inventory atomically with optimistic locking.
- Publish booking, hotel, and user events to RabbitMQ.
- Keep hotel search indexes up to date from hotel events.
- Send booking and welcome email notifications asynchronously.
- View service health in the React demo UI.

## Quick Start

Prerequisites:

- Docker 20.10+
- Docker Compose 2.x
- Java 21 for local Gradle tests
- Node 20 only when running the frontend outside Docker

Start the full demo stack:

```bash
make dev-build
```

Open:

- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080
- RabbitMQ: http://localhost:15672 (hotel_user/hotel_pass)
- Grafana: http://localhost:3001 (admin/admin123)
- Prometheus: http://localhost:9090

Check health:

```bash
make health
```

## Monitoring

Compose provisions the **Hotel Platform — Services & Dependencies** dashboard at
[Grafana](http://localhost:3001/d/hotel-platform). Prometheus is at
[localhost:9090](http://localhost:9090/targets), and Alertmanager is at
[localhost:9093](http://localhost:9093). Monitoring UIs bind to localhost.

| Target | Metrics endpoint inside the Compose network |
| --- | --- |
| Gateway / User / Hotel / Booking / Search / Notification | Ports 9000 / 9001 / 9002 / 9003 / 9004 / 9005, respectively, at /actuator/prometheus |
| PostgreSQL | postgres-exporter:9187/metrics |
| Redis | redis-exporter:9121/metrics |
| RabbitMQ | rabbitmq:15692/metrics (native plugin) |
| Meilisearch | meilisearch:7700/metrics (authenticated, experimental in v1.6) |
| Frontend | nginx-exporter:9113/metrics; reads the internal frontend:8086/stub_status listener |
| Prometheus / Grafana / Alertmanager | Their internal HTTP /metrics endpoints |

Management ports and exporters are not published to the host. Java services use
/healthz on their application port for container health checks; the Gateway keeps
/actuator/health and /health/<service> as compatibility routes. Local Java runs
bind management endpoints to loopback unless MANAGEMENT_SERVER_ADDRESS is set.

The dashboard covers target and backend health, HTTP throughput/5xx/p95, booking
API outcomes, JVM heap/threads, database/cache/broker/search metrics, and frontend
traffic. HTTP p95 uses histogram buckets; idle services can have no p95. The
Notification worker primarily handles RabbitMQ messages, not business HTTP APIs.
Booking HTTP outcomes count requests, including idempotent retries, not unique bookings.
Gateway business traffic uses completed route metrics to exclude health routes
(the general HTTP timer reports uri=UNKNOWN); rejections before routing are not
included in those panels. Other Java services use their HTTP server timers.

Prometheus evaluates three alert rules:

- ServiceDown: a metrics target is unreachable for 1 minute.
- BackendUnavailable: PostgreSQL, Redis, or NGINX is unreachable by its exporter for 1 minute.
- HighHttpErrorRate: 5xx ratio exceeds 5% over 2 minutes, sustained for 1 minute,
  with more than 6 business HTTP requests/minute. Health and scrape requests are excluded.

An UP exporter is not proof its backend is available; check pg_up, redis_up and
nginx_up as well. These rules do not detect a silently stalled outbox or a missing
scrape configuration. Inspect asynchronous delivery independently.

Alertmanager defaults to a local UI receiver with no external deliveries. Configure
a webhook/email receiver in a local override before relying on notifications, set
send_resolved: true, and verify both firing and resolved messages at the receiver.
Do not commit destination URLs, credentials, or fault-test results.

The development Meilisearch metrics key defaults to the local master key. For the
production Compose override, create a key restricted to metrics.get and supply
MEILISEARCH_METRICS_KEY. Exporter database/cache credentials follow the corresponding
Compose environment; restrict exporter credentials before external deployment.

Validate configuration and the alert rules (no services need to be stopped):

```bash
docker compose config --quiet
docker run --rm --entrypoint /bin/promtool \
  -v "$PWD/monitoring:/work:ro" prom/prometheus:v3.16.0 \
  test rules /work/tests/alerts.test.yml
```

The rule suite covers pending/firing/resolution, backend versus exporter health,
zero/low traffic, healthy traffic, missing data, and health-check exclusion.
For live fault validation use a dedicated local Compose project, restore stopped
services, and check HTTP recovery, every target's sample data, and notification
receipts separately. Local load/fault scripts and their results are kept outside
this repository.

## Demo Script

1. Open http://localhost:3000.
2. Confirm service health from the top strip.
3. Select a hotel and room.
4. Register with the seeded demo form, or log in with an existing user.
5. Check room availability for the prefilled future dates.
6. Create a booking.
7. Cancel it while it is still outside the 24-hour cancellation window.

The seeded room inventory is initialized for the next 395 days from database bootstrap. Pick dates inside that range for the most reliable demo.

## Local Backend Commands

```bash
./gradlew build
./gradlew test
./gradlew :services:booking-service:test
./gradlew :services:user-service:bootRun
```

## Local Frontend Commands

```bash
cd frontend
npm install
npm run dev
npm run build
npm test -- --run
```

The frontend defaults to `http://localhost:8080/api/v1`. Override it with:

```bash
VITE_API_BASE_URL=http://localhost:8080/api/v1 npm run dev
```

## API Examples

Register:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"password123","fullName":"Demo Guest","phone":"+1-555-0100"}'
```

List hotels:

```bash
curl http://localhost:8080/api/v1/hotels
```

Create booking:

```bash
curl -X POST http://localhost:8080/api/v1/bookings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <jwt>" \
  -H "Idempotency-Key: <stable-unique-key>" \
  -d '{"userId":"<user-id>","roomTypeId":"660e8400-e29b-41d4-a716-446655440001","checkInDate":"2026-05-17","checkOutDate":"2026-05-19","guests":2}'
```

Cancel booking:

```bash
curl -X PUT http://localhost:8080/api/v1/bookings/<booking-id>/cancel \
  -H "Authorization: Bearer <jwt>"
```

## Make Targets

```bash
make dev         # Start development stack
make dev-build   # Rebuild and start development stack
make dev-down    # Stop development stack
make infra-up    # Start PostgreSQL, Redis, RabbitMQ, Meilisearch
make logs-f      # Follow all logs
make health      # Check service health endpoints
make clean       # Remove containers, networks, and volumes
```

## Configuration

Use `.env.example` as the template. Keep these values environment-driven:

- `JWT_SECRET`
- `INTERNAL_SERVICE_SECRET`
- `DB_*`
- `REDIS_*`
- `RABBITMQ_*`
- `MEILISEARCH_*`
- `MAIL_*`
- service URLs such as `HOTEL_SERVICE_URL`, `BOOKING_SERVICE_URL`, and `USER_SERVICE_URL`

Do not commit real secrets.

## Troubleshooting

- `validate` fails on startup: rebuild or reset the local PostgreSQL volume so `init-db/init.sql` can add missing version columns.
- Booking creation says no inventory: choose dates within the initialized inventory window or call the inventory initialization endpoint.
- Emails fail locally: the notification service still processes events, but SMTP needs valid `MAIL_*` credentials.
- Frontend cannot reach APIs: confirm the gateway is up on port `8080` and `CORS_ALLOWED_ORIGINS` includes `http://localhost:3000`.
- Rabbit events do not flow: confirm RabbitMQ is healthy and all services share the same exchange/routing key configuration.

More details are in [docs/architecture.md](docs/architecture.md).
