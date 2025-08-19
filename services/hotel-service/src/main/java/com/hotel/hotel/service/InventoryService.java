package com.hotel.hotel.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    
    private final RestTemplate restTemplate;
    
    public Integer getAvailableRooms(UUID roomTypeId, LocalDate date) {
        try {
            String url = "http://booking-service:8083/api/v1/inventory/availability"
                    + "?roomTypeId=" + roomTypeId
                    + "&date=" + date;
            
            log.debug("Checking room availability: {}", url);
            
            Integer availableRooms = restTemplate.getForObject(url, Integer.class);
            
            if (availableRooms == null) {
                log.warn("No availability data returned for roomType {} on date {}", roomTypeId, date);
                return 0;
            }
            
            log.debug("Available rooms for {} on {}: {}", roomTypeId, date, availableRooms);
            return availableRooms;
            
        } catch (RestClientException e) {
            log.error("Failed to fetch room availability for roomType {} on date {}", roomTypeId, date, e);
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