package com.hotel.hotel.service;

import com.hotel.hotel.exception.InventoryCommunicationException;
import com.hotel.hotel.exception.InventoryLifecycleConflictException;
import com.hotel.hotel.security.InternalServiceTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryServiceContractTest {

    @Test
    void getAvailableRooms_UsesUnifiedExactDateContract() {
        AtomicReference<org.springframework.web.reactive.function.client.ClientRequest> captured =
                new AtomicReference<>();
        ExchangeFunction exchange = request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("4")
                    .build());
        };
        InventoryService service = service(exchange);
        UUID roomTypeId = UUID.randomUUID();
        LocalDate date = LocalDate.now();

        assertEquals(4, service.getAvailableRooms(roomTypeId, date));
        assertEquals("/api/v1/inventory/availability", captured.get().url().getPath());
        assertEquals(roomTypeId.toString(),
                queryValue(captured.get().url().getQuery(), "roomTypeId"));
        assertEquals(date.toString(), queryValue(captured.get().url().getQuery(), "date"));
        assertEquals("hotel-service", captured.get().headers().getFirst("X-Internal-Service"));
        assertNotNull(captured.get().headers().getFirst("X-Internal-Token"));
    }

    @Test
    void getAvailableRooms_RemoteFailureNeverPretendsZero() {
        InventoryService service = service(request -> Mono.just(
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));

        assertThrows(InventoryCommunicationException.class,
                () -> service.getAvailableRooms(UUID.randomUUID(), LocalDate.now()));
    }

    @Test
    void deleteInventory_RemoteConflictIsExplicit() {
        InventoryService service = service(request -> Mono.just(
                ClientResponse.create(HttpStatus.CONFLICT).build()));

        assertThrows(InventoryLifecycleConflictException.class,
                () -> service.deleteInventory(UUID.randomUUID()));
    }

    private InventoryService service(ExchangeFunction exchange) {
        InternalServiceTokenService tokenService = new InternalServiceTokenService();
        ReflectionTestUtils.setField(tokenService, "serviceSecret", "test-shared-secret");
        InventoryService service = new InventoryService(
                WebClient.builder().exchangeFunction(exchange), tokenService);
        ReflectionTestUtils.setField(service, "bookingServiceUrl", "http://booking-service:8083");
        return service;
    }

    private String queryValue(String query, String key) {
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(key)) {
                return parts[1];
            }
        }
        return null;
    }
}
