package com.hotel.hotel.service;

import com.hotel.hotel.exception.InventoryCommunicationException;
import com.hotel.hotel.exception.InventoryLifecycleConflictException;
import com.hotel.hotel.security.InternalServiceTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    public static final int BOOKING_HORIZON_DAYS = 395;
    
    private final WebClient.Builder webClientBuilder;
    private final InternalServiceTokenService tokenService;

    @Value("${services.booking-service.url:http://booking-service:8083}")
    private String bookingServiceUrl;
    
    public Integer getAvailableRooms(UUID roomTypeId, LocalDate date) {
        try {
            // Use async method and block for backward compatibility
            return getAvailableRoomsAsync(roomTypeId, date)
                .timeout(Duration.ofSeconds(5))
                .block();
            
        } catch (Exception e) {
            log.error("Error fetching room availability for roomType {} on date {}", roomTypeId, date, e);
            if (e instanceof InventoryCommunicationException communicationException) {
                throw communicationException;
            }
            throw new InventoryCommunicationException("Booking inventory is unavailable", e);
        }
    }

    public Mono<Integer> getAvailableRoomsAsync(UUID roomTypeId, LocalDate date) {
        log.debug("Checking room availability async for roomType {} on date {}", roomTypeId, date);
        
        WebClient webClient = inventoryWebClient();
        
        return webClient.get()
            .uri("/api/v1/inventory/availability?roomTypeId={roomTypeId}&date={date}",
                 roomTypeId, date)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> response.createException()
                    .map(error -> new InventoryCommunicationException(
                            "Unable to load room availability for " + roomTypeId + " on " + date, error)))
            .bodyToMono(Integer.class)
            .doOnNext(availableRooms -> 
                log.debug("Available rooms for {} on {}: {}", roomTypeId, date, availableRooms))
            .switchIfEmpty(Mono.error(new InventoryCommunicationException(
                    "Booking inventory returned an empty availability response")));
    }

    public Mono<Map<UUID, Integer>> getAvailableRoomsForTodayBatch(List<UUID> roomTypeIds) {
        if (roomTypeIds == null || roomTypeIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        log.debug("Fetching batch room availability for {} room types", roomTypeIds.size());
        WebClient webClient = inventoryWebClient();

        return webClient.post()
                .uri("/api/v1/inventory/availabilities-for-today")
                .bodyValue(roomTypeIds)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.createException()
                        .map(error -> new InventoryCommunicationException(
                                "Unable to load today's room availability", error)))
                .bodyToMono(new ParameterizedTypeReference<Map<UUID, Integer>>() {})
                .map(availability -> {
                    List<UUID> missing = roomTypeIds.stream().distinct()
                            .filter(id -> !availability.containsKey(id))
                            .toList();
                    if (!missing.isEmpty()) {
                        throw new InventoryCommunicationException(
                                "Booking inventory omitted room types: " + missing);
                    }
                    return availability;
                })
                .switchIfEmpty(Mono.error(new InventoryCommunicationException(
                        "Booking inventory returned an empty batch response")))
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(error -> error instanceof InventoryCommunicationException
                        ? error
                        : new InventoryCommunicationException("Booking inventory is unavailable", error));
    }
    
    public Integer getAvailableRoomsForToday(UUID roomTypeId) {
        return getAvailableRooms(roomTypeId, LocalDate.now());
    }

    public void initializeInventory(UUID roomTypeId, int totalRooms) {
        executeLifecycleRequest(inventoryWebClient().post()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/inventory/initialize")
                        .queryParam("roomTypeId", roomTypeId)
                        .queryParam("totalRooms", totalRooms)
                        .queryParam("daysAhead", BOOKING_HORIZON_DAYS)
                        .build()));
    }

    public void setDesiredCapacity(UUID roomTypeId, int totalRooms) {
        executeLifecycleRequest(inventoryWebClient().put()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/inventory/{roomTypeId}/capacity")
                        .queryParam("totalRooms", totalRooms)
                        .queryParam("daysAhead", BOOKING_HORIZON_DAYS)
                        .build(roomTypeId)));
    }

    public void deleteInventory(UUID roomTypeId) {
        executeLifecycleRequest(inventoryWebClient().delete()
                .uri("/api/v1/inventory/{roomTypeId}", roomTypeId));
    }

    public void deleteInventories(List<UUID> roomTypeIds) {
        if (roomTypeIds == null || roomTypeIds.isEmpty()) {
            return;
        }
        executeLifecycleRequest(inventoryWebClient().post()
                .uri("/api/v1/inventory/deactivate")
                .bodyValue(roomTypeIds));
    }

    private WebClient inventoryWebClient() {
        return webClientBuilder.baseUrl(bookingServiceUrl)
                .defaultHeader("X-Internal-Service", "hotel-service")
                .defaultHeader("X-Internal-Token", tokenService.generateToken("hotel-service"))
                .build();
    }

    private void executeLifecycleRequest(WebClient.RequestHeadersSpec<?> request) {
        try {
            request.retrieve()
                    .onStatus(status -> status.value() == 409, response -> response.createException())
                    .onStatus(HttpStatusCode::isError, response -> response.createException())
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (WebClientResponseException.Conflict e) {
            throw new InventoryLifecycleConflictException(
                    "Inventory change conflicts with active bookings");
        } catch (Exception e) {
            throw new InventoryCommunicationException("Unable to update booking inventory", e);
        }
    }
}
