package com.hotel.hotel.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    
    private final WebClient.Builder webClientBuilder;
    
    public Integer getAvailableRooms(UUID roomTypeId, LocalDate date) {
        try {
            WebClient webClient = webClientBuilder.build();
            
            log.debug("Checking room availability for roomType {} on date {}", roomTypeId, date);
            
            Integer availableRooms = webClient.get()
                .uri("http://booking-service:8083/api/v1/inventory/availability?roomTypeId={roomTypeId}&date={date}",
                     roomTypeId, date)
                .retrieve()
                .bodyToMono(Integer.class)
                .block(Duration.ofSeconds(5));
            
            if (availableRooms == null) {
                log.warn("No availability data returned for roomType {} on date {}", roomTypeId, date);
                return 0;
            }
            
            log.debug("Available rooms for {} on {}: {}", roomTypeId, date, availableRooms);
            return availableRooms;
            
        } catch (WebClientException e) {
            log.error("WebClient error fetching room availability for roomType {} on date {}", roomTypeId, date, e);
            // Return 0 for safety - better to show unavailable than oversell
            return 0;
        } catch (Exception e) {
            log.error("Unexpected error fetching room availability", e);
            return 0;
        }
    }
    
    public Integer getAvailableRoomsForToday(UUID roomTypeId) {
        return getAvailableRooms(roomTypeId, LocalDate.now());
    }
}