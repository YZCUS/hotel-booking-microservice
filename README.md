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
