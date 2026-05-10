# Architecture Notes

## Service Boundaries

The repository keeps six Spring Boot services behind a Spring Cloud Gateway:

- `user-service`: owns users, credentials, profile cache, JWT issuing, and internal user lookups.
- `hotel-service`: owns hotels, room types, favorites, and hotel domain events.
- `booking-service`: owns bookings, room inventory, pricing orchestration, and booking events.
- `search-service`: owns Meilisearch index operations and search endpoints.
- `notification-service`: owns email rendering and async notification handling.
- `api-gateway`: owns public routing, authentication propagation, CORS, and rate limiting.

Local development uses one PostgreSQL database with service-specific schemas. Cross-service foreign keys are avoided where the owning service is different.

## Data and Consistency

`booking-service` uses `@Version` on bookings and room inventory. Inventory reservation reads date-range rows with write locks, verifies every night in the requested stay exists, and updates all dates in the same transaction as booking creation. If booking persistence fails, the transaction rolls back the inventory updates; no manual compensation runs inside the same transaction.

Cancellation uses the same date-level 24-hour rule for the command and the `canCancel` response: a booking can be cancelled through the day before check-in and cannot be cancelled on the check-in date.

## Events

RabbitMQ topic exchanges carry the async integration events:

- `booking.exchange`: `booking.created`, `booking.cancelled`
- `hotel.exchange`: `hotel.created`, `hotel.updated`, `hotel.deleted`
- `user.exchange`: `user.registered`

Booking, hotel, and user publishers schedule Rabbit publication after transaction commit where the service method is transactional. Event publication failures are logged after commit so they do not roll back already committed user-facing work. A production system should add an outbox table and replay worker for stronger delivery guarantees.

## Cache Choices

Redis remains useful for user profile caching, rate limiting, and selected lookup caches. Two unsafe patterns were removed or reduced:

- Booking availability cache is fully cleared when inventory changes. This avoids stale availability from partial key eviction.
- Hotel search no longer stores whole Spring `Page` objects or scans Redis with `KEYS search:*`. Search results are fetched from the database and can be reintroduced later with bounded cache keys and a cache manager.

## Internal Service Auth

Notification-to-user lookups include both `X-Internal-Service` and `X-Internal-Token`. The token is a short SHA-256 based value over service name, shared secret, and minute timestamp. This keeps notification-service from bypassing user-service security with only a service-name header.

Use a strong shared `INTERNAL_SERVICE_SECRET` in real environments and rotate it like any other secret.

## Frontend

The `frontend/` app is a Vite React TypeScript demo for recruiter and local walkthroughs. It talks to the gateway at `VITE_API_BASE_URL`, defaulting to `http://localhost:8080/api/v1`, and supports:

- hotel browsing
- room selection
- registration and login
- availability check
- booking creation
- booking cancellation
- health overview

## Known Production Tradeoffs

- Shared local PostgreSQL is convenient for demos, but production should isolate service storage and migrations.
- Rabbit publishing is post-commit but not outbox-backed, so a broker outage after commit can drop an event.
- Pricing still synchronously waits on hotel-service data for the legacy booking path.
- Search indexing depends on event delivery and currently has no explicit reindex reconciliation job.
- Health checks are basic actuator checks, not business transaction probes.
- The demo frontend stores JWTs in local storage for simplicity; production should revisit token storage and refresh strategy.

## Risk Register

- Performance: blocking WebClient calls in pricing and notification paths can tie up request threads under downstream latency.
- Memory: avoid caching large serialized object graphs in Redis; prefer small DTOs or bounded cache entries.
- Concurrency: booking inventory updates rely on database locking and version columns; schema drift can disable the intended safety.
- Database: `ddl-auto=validate` is correct for service startup, but schema migrations should move to Flyway or Liquibase.
- Messaging: event consumers should eventually get dead-letter monitoring, replay tooling, and idempotency keys.
