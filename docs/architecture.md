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

Hotel room lifecycle commands flush the local catalog change before mutating booking inventory. They also register an inverse remote operation for local transaction rollback. This is compensation, not a distributed transaction: it handles ordinary local rollback after remote success, but a process crash or failed compensation still requires operational reconciliation.

## Events

RabbitMQ topic exchanges carry the async integration events:

- `booking.exchange`: `booking.created.v2`, `booking.cancelled.v2`
- `hotel.exchange`: `hotel.created.v2`, `hotel.updated.v2`, `hotel.deleted.v2`
- `user.exchange`: `user.registered.v2`

Booking, hotel, and user services write events to a schema-owned outbox in the same database transaction as the domain change. A relay publishes persistent JSON messages, waits for both broker confirms and unroutable-message returns, and commits each acknowledged outbox row in its own transaction before moving to the next event. Failed publications retry with bounded exponential backoff. This closes the database-commit-to-broker failure window and prevents a crash from replaying an entire acknowledged batch, while retaining at-least-once delivery semantics.

Consumers use versioned `.queue.v2`, `.dlq.v2`, and `.dlx.v2` names. This avoids RabbitMQ declaration failures when upgrading an existing environment whose legacy dead-letter exchanges were created with a different type. Deploy search and notification consumers before the three producers; then drain any legacy unversioned queues before deleting the old queues and dead-letter exchanges. An outbox entry remains pending if its v2 route has no consumer queue.

Search listeners rethrow failed indexing operations so RabbitMQ can retry or dead-letter the event. An hourly full-export reconciliation repairs missed index updates and removes stale documents only after Meilisearch confirms the replacement task succeeded. Notification handlers acknowledge messages only after email delivery completes; failed messages follow their configured dead-letter route.

## Cache Choices

Redis remains useful for user profile caching, rate limiting, and selected lookup caches. Two unsafe patterns were removed or reduced:

- Booking availability cache is fully cleared when inventory changes. This avoids stale availability from partial key eviction.
- Hotel search no longer stores whole Spring `Page` objects or scans Redis with `KEYS search:*`. Search results are fetched from the database and can be reintroduced later with bounded cache keys and a cache manager.

## Internal Service Auth

Notification-to-user lookups include both `X-Internal-Service` and `X-Internal-Token`. The token is a short SHA-256 based value over service name, shared secret, and minute timestamp. This keeps notification-service from bypassing user-service security with only a service-name header.

Use a strong shared `INTERNAL_SERVICE_SECRET` in real environments and rotate it like any other secret.

## Frontend

The `frontend/` app is a Vite React TypeScript demo for recruiter and local walkthroughs. Local development talks to the gateway at `VITE_API_BASE_URL`, defaulting to `http://localhost:8080/api/v1`. The production container defaults to `/api/v1`, with nginx proxying same-origin API and health requests to `api-gateway`, so the browser never depends on a localhost gateway. It supports:

- hotel browsing
- room selection
- registration and login
- availability check
- booking creation
- booking cancellation
- health overview

## Known Production Tradeoffs

- Shared local PostgreSQL is convenient for demos, but production should isolate service storage and migrations.
- Pricing still synchronously waits on hotel-service data for the legacy booking path.
- Hotel catalog changes synchronously call booking-service to provision inventory. Local pre-flush and rollback compensation narrow the inconsistency window, but retries and reconciliation are still required when the remote outcome is ambiguous, the process crashes, or compensation fails.
- Outbox delivery is at least once. Search writes are naturally repeatable, but notification delivery still needs an event-inbox/deduplication table to eliminate the small duplicate-email window after an external mail send succeeds but before RabbitMQ records the acknowledgement.
- Health checks are basic actuator checks, not business transaction probes.
- The demo frontend stores JWTs in local storage for simplicity; production should revisit token storage and refresh strategy.

## Risk Register

- Performance: blocking WebClient calls in pricing and notification paths can tie up request threads under downstream latency.
- Memory: avoid caching large serialized object graphs in Redis; prefer small DTOs or bounded cache entries.
- Concurrency: booking inventory updates rely on database locking and version columns; schema drift can disable the intended safety.
- Database: booking-service validates its schema, but user-service and hotel-service still use `ddl-auto=update`; all three schemas should move to Flyway or Liquibase before production rollout.
- Messaging: dead-letter queues need alerting and replay tooling; notification consumers also need durable event-id deduplication.
