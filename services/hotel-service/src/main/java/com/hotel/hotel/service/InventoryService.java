package com.hotel.hotel.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
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
    
    private final WebClient.Builder webClientBuilder;
    
    @Cacheable(value = "room-availability", 
               key = "#roomTypeId + '_' + #date",
               unless = "#result == null || #result == 0")
    public Integer getAvailableRooms(UUID roomTypeId, LocalDate date) {
        try {
            // Use async method and block for backward compatibility
            return getAvailableRoomsAsync(roomTypeId, date)
                .timeout(Duration.ofSeconds(5))
                .block();
            
        } catch (Exception e) {
            log.error("Error fetching room availability for roomType {} on date {}", roomTypeId, date, e);
            return 0; // Safe fallback
        }
    }
    
    /**
     * Async version for better performance - non-blocking
     */
    public Mono<Integer> getAvailableRoomsAsync(UUID roomTypeId, LocalDate date) {
        log.debug("Checking room availability async for roomType {} on date {}", roomTypeId, date);
        
        WebClient webClient = webClientBuilder
            .baseUrl("http://booking-service:8083")
            .build();
        
        return webClient.get()
            .uri("/api/v1/inventory/availability?roomTypeId={roomTypeId}&date={date}",
                 roomTypeId, date)
            .retrieve()
            .bodyToMono(Integer.class)
            .doOnNext(availableRooms -> 
                log.debug("Available rooms for {} on {}: {}", roomTypeId, date, availableRooms))
            .onErrorReturn(WebClientException.class, 0)
            .onErrorReturn(Exception.class, 0)
            .defaultIfEmpty(0);
    }

    public Mono<Map<UUID, Integer>> getAvailableRoomsForTodayBatch(List<UUID> roomTypeIds) {
        if (roomTypeIds == null || roomTypeIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        log.debug("Fetching batch room availability for {} room types", roomTypeIds.size());
        WebClient webClient = webClientBuilder.baseUrl("http://booking-service:8083").build();

        return webClient.post()
                .uri("/api/v1/inventory/availabilities-for-today")
                .bodyValue(roomTypeIds)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<UUID, Integer>>() {})
                .onErrorReturn(Map.of()); // return empty map on error
    }
    
    public Integer getAvailableRoomsForToday(UUID roomTypeId) {
        return getAvailableRooms(roomTypeId, LocalDate.now());
    }
}