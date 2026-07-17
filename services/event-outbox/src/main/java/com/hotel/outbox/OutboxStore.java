package com.hotel.outbox;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class OutboxStore {

    private static final String TABLE_NAME = "outbox_events";
    private static final String INSERT_SQL_TEMPLATE = """
            INSERT INTO outbox_events
                (id, exchange_name, routing_key, event_type, payload, attempts,
                 next_attempt_at, created_at)
            VALUES (?, ?, ?, ?, ?, 0, ?, ?)
            """;

    private static final String LOCK_PENDING_SQL_TEMPLATE = """
            SELECT id, exchange_name, routing_key, event_type, payload, attempts
            FROM outbox_events
            WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY created_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final String insertSql;
    private final String lockPendingSql;

    OutboxStore(JdbcTemplate jdbcTemplate, String schema) {
        this.jdbcTemplate = jdbcTemplate;
        if (schema == null || !schema.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid outbox schema: " + schema);
        }
        this.tableName = schema + "." + TABLE_NAME;
        this.insertSql = qualify(INSERT_SQL_TEMPLATE);
        this.lockPendingSql = qualify(LOCK_PENDING_SQL_TEMPLATE);
    }

    UUID enqueue(String exchange, String routingKey, String eventType, String payload) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(
                insertSql, id, exchange, routingKey, eventType, payload, now, now);
        return id;
    }

    List<PendingOutboxEvent> lockPending(int batchSize) {
        return jdbcTemplate.query(
                lockPendingSql,
                (resultSet, rowNumber) -> new PendingOutboxEvent(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("exchange_name"),
                        resultSet.getString("routing_key"),
                        resultSet.getString("event_type"),
                        resultSet.getString("payload"),
                        resultSet.getInt("attempts")),
                batchSize);
    }

    void markPublished(UUID id, LocalDateTime publishedAt) {
        jdbcTemplate.update(
                "UPDATE " + tableName + " SET published_at = ?, last_error = NULL WHERE id = ?",
                Timestamp.valueOf(publishedAt), id);
    }

    void markFailed(UUID id, int attempts, LocalDateTime nextAttemptAt, String error) {
        jdbcTemplate.update(
                "UPDATE " + tableName + " " + """
                SET attempts = ?, next_attempt_at = ?, last_error = ?
                WHERE id = ? AND published_at IS NULL
                """,
                attempts, Timestamp.valueOf(nextAttemptAt), truncate(error), id);
    }

    void deletePublishedBefore(LocalDateTime cutoff) {
        jdbcTemplate.update(
                "DELETE FROM " + tableName + " WHERE published_at < ?",
                Timestamp.valueOf(cutoff));
    }

    private String truncate(String error) {
        if (error == null) {
            return "Unknown publication failure";
        }
        return error.length() <= 2000 ? error : error.substring(0, 2000);
    }

    private String qualify(String sql) {
        return sql.replace(TABLE_NAME, tableName);
    }

    record PendingOutboxEvent(
            UUID id,
            String exchange,
            String routingKey,
            String eventType,
            String payload,
            int attempts) {
    }
}
