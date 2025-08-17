# Scalable Hotel Reservation System

A modern, containerized microservices system for hotel reservations. It’s designed to run cost‑efficiently on a single host (e.g., one EC2 instance) while keeping a clean path to horizontal scaling.

## What’s inside

- Runtime: Docker + Docker Compose
- Language/Frameworks: Java 21, Spring Boot 3.2.x, Spring Cloud Gateway 2023.0.x
- Data & Infra: PostgreSQL 15, Redis 7, RabbitMQ 3, Meilisearch 1.x
- Build: Gradle 8.x (wrapper included)
- Observability: Prometheus, Grafana
 - Database Model (Free Tier): Single PostgreSQL database with per-service schemas (logical isolation)

## Services and ports

- API Gateway (8080) — routing, auth, CORS
- User Service (8081) — registration, login, profile, JWT
- Hotel Service (8082) — hotels, room types, inventory
- Booking Service (8083) — bookings, optimistic locking, lifecycle
- Search Service (8084) — hotel search via Meilisearch
- Notification Service (8085) — async notifications (email)
- PostgreSQL (5432), Redis (6379), RabbitMQ (5672/15672 UI), Meilisearch (7700)
- Prometheus (9090), Grafana (3001)

These ports match docker-compose.yml.

## Quick start

- Recommended: use the Makefile targets
  - Development: `make dev` (or `make dev-build` on first run)
  - Production-like: `make prod` (detached) or `make prod-build`
  - Stop: `make dev-down` or `make prod-down`
  - Logs: `make logs` or `make logs-f`
- Direct compose (equivalent):
  - Dev: `docker-compose -f docker-compose.yml -f docker-compose.dev.yml up`
  - Prod: `docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d`

Environment variables: you can create a `.env` at the repo root to override defaults in compose (e.g., `JWT_SECRET`, `MAIL_*`, `CORS_ALLOWED_ORIGINS`). If not set, sensible defaults in docker-compose.yml are used for local runs.

## Health checks

Each service exposes `/actuator/health`:
- http://localhost:8080/actuator/health (Gateway)
- http://localhost:8081/actuator/health (User)
- http://localhost:8082/actuator/health (Hotel)
- http://localhost:8083/actuator/health (Booking)
- http://localhost:8084/actuator/health (Search)
- http://localhost:8085/actuator/health (Notification)

You can also run `make health`.

## Monitoring and tooling

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001 (default admin/admin123; change in production)
- RabbitMQ UI: http://localhost:15672 (default hotel_user/hotel_pass)

## Data initialization

- Database bootstrap scripts live in `init-db/` and are mounted into the PostgreSQL container on first start.
 - Free-tier friendly model: one database (`hotel_reservation`) with per-service schemas:
   - `user_svc` (users)
   - `hotel_svc` (hotels, room_types, user_favorites)
   - `booking_svc` (bookings, room_inventory)
   - `search_svc` (search_history)
 - JPA default schema settings (applied in service configs):
   - User Service: `hibernate.default_schema = user_svc` (docker profile)
   - Hotel Service: `hibernate.default_schema = hotel_svc` (docker profile)
   - Booking Service: `hibernate.default_schema = booking_svc`
 - Bring up only DB to initialize schemas (optional): `docker-compose up -d postgres`

## Project structure

```
hotel-booking-parent/
├── docker-compose.yml
├── docker-compose.dev.yml
├── docker-compose.prod.yml
├── Makefile
├── init-db/
├── monitoring/
├── services/
│   ├── api-gateway/
│   ├── user-service/
│   ├── hotel-service/
│   ├── booking-service/
│   ├── search-service/
│   └── notification-service/
├── build.gradle
├── settings.gradle
└── gradle/wrapper/ ...
```

## Development workflow

- Edit code under `services/<service-name>`.
- Build and run with Docker Compose (preferred). The compose files handle ports, dependencies, health checks, and restart policies.
- Tests: there are service-level tests; you can run `make test` when the dev stack is up, or run Gradle tests in each service locally.

- Java version: use JDK 21 for local development.
- Gradle setup: root project is aggregator-only (no root Boot JAR). Build all services with `./gradlew build`, or build a single service with `./gradlew :services:<service-name>:build`. Run a service locally with `./gradlew :services:<service-name>:bootRun`.

## API overview

Through the API Gateway (prefix often `/api/v1/`):
- Auth/User: register, login, profile CRUD
- Hotels: list hotels, hotel details, room types
- Bookings: create booking, fetch by id/user, check-in flow
- Search: hotel search endpoints backed by Meilisearch
- Notifications are event-driven (no public endpoints)

See source under each service’s `controller` and `resources` for exact endpoints and request/response models.

## Security notes

- JWT authentication with configurable secret (`JWT_SECRET`)
- BCrypt password hashing
- Rate limiting/CORS via gateway configuration

## Production tips

- Provide strong secrets and credentials via environment variables
- Enable TLS on your ingress / gateway
- Set up backups for PostgreSQL and Meilisearch volumes
- Configure proper dashboards and alerts in Grafana/Prometheus

## Additional docs

This README has been consolidated. The Docker guide and implementation notes are now included below.

### Docker Guide (Consolidated from former README-Docker.md)

#### Overview

This section provides comprehensive guidance for running the system using Docker Compose. The system consists of multiple microservices and infrastructure components.

#### Quick Start

- Prerequisites: Docker 20.10+, Docker Compose 2.0+, 8GB RAM, 10GB disk
- Environment setup:
  - Copy env: `cp .env.example .env`
  - Update secrets (JWT_SECRET, email settings)
- Start:
  - Dev: `make dev` or `docker-compose -f docker-compose.yml -f docker-compose.dev.yml up`
  - Prod: `make prod` or `docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d`

#### Available Commands (Makefile)

```bash
make dev         # start dev
make dev-build   # build + start dev
make dev-down    # stop dev
make prod        # start prod
make prod-build  # build + start prod
make prod-down   # stop prod
make build       # build all services
make up          # start default
make down        # stop all
make logs        # show logs
make logs-f      # follow logs
make clean       # cleanup
```

#### Services and Ports

| Service | Port | Description |
|---------|------|-------------|
| API Gateway | 8080 | Main entry point |
| User Service | 8081 | User management and authentication |
| Hotel Service | 8082 | Hotel and room management |
| Booking Service | 8083 | Booking and inventory management |
| Search Service | 8084 | Hotel search functionality |
| Notification Service | 8085 | Email notifications |
| PostgreSQL | 5432 | Main database |
| Redis | 6379 | Caching and sessions |
| RabbitMQ | 5672/15672 | Message queue (UI: 15672) |
| Meilisearch | 7700 | Search engine |
| Prometheus | 9090 | Metrics collection |
| Grafana | 3001 | Monitoring dashboards |

#### Health Checks

```bash
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # User
curl http://localhost:8082/actuator/health  # Hotel
curl http://localhost:8083/actuator/health  # Booking
curl http://localhost:8084/actuator/health  # Search
curl http://localhost:8085/actuator/health  # Notification
```

#### Troubleshooting (highlights)

- Port conflicts: check and adjust compose mapping
- Memory issues: `docker stats` / increase Docker Desktop memory
- Startup order: `docker-compose ps` and inspect infra logs
- DB access: `docker-compose exec postgres psql -U hotel_user -d hotel_reservation -c "SELECT version();"`

### Implementation Notes (Consolidated from former README_IMPLEMENTATION.md)

#### Implemented Services

- User Service: registration, auth, JWT, Redis cache
- Hotel Service: hotels, room types, favorites, Redis caching
- Booking Service: booking flow, optimistic locking, RabbitMQ
- Search Service: Meilisearch integration, WebFlux
- Notification Service: async email via RabbitMQ
- API Gateway: routing, JWT, rate limiting

#### Development

Backend:
```bash
./gradlew build
./gradlew test
./gradlew user-service:bootRun
```

Frontend (if applicable):
```bash
cd frontend
npm install
npm run dev
```

#### Architecture Highlights

- DDD boundaries, event-driven via RabbitMQ, API Gateway pattern
- Database-per-service, Redis caching, health checks, structured logging
- Security: JWT, validation, CORS, BCrypt

#### Testing

- JUnit 5, Mockito, Testcontainers, Spring Boot Test, H2 for unit tests

#### Production Considerations

- Strong secrets via env vars, TLS, backups, dashboards/alerts
- Resource limits configured in docker-compose.prod.yml

### GHCR images and release

- Automatic release: pushing a Git tag (e.g., `v1.0.0`) triggers the CD workflow to build and push Docker images for all services to GHCR.
- Image naming: `ghcr.io/<owner>/<repo>-<service>:<tag>`
  - Examples:
    - `ghcr.io/<owner>/<repo>-api-gateway:v1.0.0`
    - `ghcr.io/<owner>/<repo>-user-service:v1.0.0`
    - `ghcr.io/<owner>/<repo>-hotel-service:v1.0.0`
    - `ghcr.io/<owner>/<repo>-booking-service:v1.0.0`
    - `ghcr.io/<owner>/<repo>-search-service:v1.0.0`
    - `ghcr.io/<owner>/<repo>-notification-service:v1.0.0`

#### Trigger a release (tags)

```bash
git tag v1.0.0
git push origin v1.0.0
```

Or run manually via GitHub Actions → CD → Run workflow and provide `version`.

#### Make images public (recommended)

1. Go to the repository’s Packages tab
2. Open an image (e.g., `hotel-booking-microservices-user-service`)
3. Open Package settings
4. Change Visibility to Public and save

#### Pull images (public)

```bash
docker pull ghcr.io/<owner>/<repo>-user-service:v1.0.0
docker pull ghcr.io/<owner>/<repo>-hotel-service:v1.0.0
docker pull ghcr.io/<owner>/<repo>-booking-service:v1.0.0
docker pull ghcr.io/<owner>/<repo>-search-service:v1.0.0
docker pull ghcr.io/<owner>/<repo>-notification-service:v1.0.0
docker pull ghcr.io/<owner>/<repo>-api-gateway:v1.0.0
```

#### Pull images (private)

```bash
echo <PERSONAL_ACCESS_TOKEN> | docker login ghcr.io -u <GITHUB_USERNAME> --password-stdin
docker pull ghcr.io/<owner>/<repo>-user-service:v1.0.0
```

Note: the current workflow builds `linux/amd64`. Add `arm64` in the CD workflow `platforms` if needed.
