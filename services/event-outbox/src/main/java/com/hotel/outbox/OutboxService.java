package com.hotel.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

public class OutboxService {

    private final OutboxStore outboxStore;
    private final ObjectMapper objectMapper;

    OutboxService(OutboxStore outboxStore, ObjectMapper objectMapper) {
        this.outboxStore = outboxStore;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(String exchange, String routingKey, String eventType, Object event) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            return outboxStore.enqueue(
                    requireText(exchange, "exchange"),
                    requireText(routingKey, "routingKey"),
                    requireText(eventType, "eventType"),
                    objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize outbox event " + eventType, e);
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
