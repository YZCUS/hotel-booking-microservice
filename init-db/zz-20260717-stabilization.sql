-- Idempotent migration for databases created before the microservice stabilization changes.
BEGIN;

ALTER TABLE user_svc.users
    ADD COLUMN IF NOT EXISTS role VARCHAR(30) DEFAULT 'USER' NOT NULL;

ALTER TABLE booking_svc.room_inventory
    ADD COLUMN IF NOT EXISTS total_rooms INTEGER;
ALTER TABLE booking_svc.bookings
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

UPDATE booking_svc.room_inventory ri
SET total_rooms = GREATEST(
        rt.total_inventory,
        ri.available_rooms + (
            SELECT COUNT(*)::INTEGER
            FROM booking_svc.bookings b
            WHERE b.room_type_id = ri.room_type_id
              AND b.status IN ('CONFIRMED', 'CHECKED_IN')
              AND b.check_in_date <= ri.date
              AND b.check_out_date > ri.date
        ))
FROM hotel_svc.room_types rt
WHERE ri.room_type_id = rt.id
  AND ri.total_rooms IS NULL;

UPDATE booking_svc.room_inventory ri
SET total_rooms = ri.available_rooms + (
        SELECT COUNT(*)::INTEGER
        FROM booking_svc.bookings b
        WHERE b.room_type_id = ri.room_type_id
          AND b.status IN ('CONFIRMED', 'CHECKED_IN')
          AND b.check_in_date <= ri.date
          AND b.check_out_date > ri.date)
WHERE ri.total_rooms IS NULL;

ALTER TABLE booking_svc.room_inventory
    ALTER COLUMN total_rooms SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'room_inventory_total_nonnegative'
          AND conrelid = 'booking_svc.room_inventory'::regclass
    ) THEN
        ALTER TABLE booking_svc.room_inventory
            ADD CONSTRAINT room_inventory_total_nonnegative CHECK (total_rooms >= 0);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'room_inventory_available_bounds'
          AND conrelid = 'booking_svc.room_inventory'::regclass
    ) THEN
        ALTER TABLE booking_svc.room_inventory
            ADD CONSTRAINT room_inventory_available_bounds
            CHECK (available_rooms >= 0 AND available_rooms <= total_rooms);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_bookings_user_idempotency
    ON booking_svc.bookings(user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_checked_in_room_assignment
    ON booking_svc.bookings(room_type_id, room_number)
    WHERE status = 'CHECKED_IN';

CREATE TABLE IF NOT EXISTS user_svc.outbox_events (
    id UUID PRIMARY KEY,
    exchange_name VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    attempts INTEGER DEFAULT 0 NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS hotel_svc.outbox_events
    (LIKE user_svc.outbox_events INCLUDING ALL);
CREATE TABLE IF NOT EXISTS booking_svc.outbox_events
    (LIKE user_svc.outbox_events INCLUDING ALL);

CREATE INDEX IF NOT EXISTS idx_user_outbox_pending
    ON user_svc.outbox_events(published_at, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS idx_hotel_outbox_pending
    ON hotel_svc.outbox_events(published_at, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS idx_booking_outbox_pending
    ON booking_svc.outbox_events(published_at, next_attempt_at, created_at);

INSERT INTO booking_svc.room_inventory
    (room_type_id, date, total_rooms, available_rooms)
SELECT
    rt.id,
    CURRENT_DATE + generate_series(0, 395),
    rt.total_inventory,
    rt.total_inventory
FROM hotel_svc.room_types rt
ON CONFLICT (room_type_id, date) DO NOTHING;

COMMIT;
