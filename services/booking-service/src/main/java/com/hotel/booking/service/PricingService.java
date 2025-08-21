package com.hotel.booking.service;

import com.hotel.booking.dto.RoomTypeResponse;
import com.hotel.booking.exception.ServiceCommunicationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {
    
    private final WebClient.Builder webClientBuilder;
    
    public BigDecimal calculateTotalPrice(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        log.info("Calculating total price for roomType {} from {} to {}", roomTypeId, checkIn, checkOut);
        
        try {
            // Get room type price from hotel service (now async with fallback)
            BigDecimal pricePerNight = getRoomTypePriceAsync(roomTypeId)
                .toFuture()
                .get(); // Only for backward compatibility, will be removed later
            
            // Calculate number of nights
            long numberOfNights = ChronoUnit.DAYS.between(checkIn, checkOut);
            
            // Calculate base price
            BigDecimal basePrice = pricePerNight.multiply(BigDecimal.valueOf(numberOfNights));
            
            // Apply dynamic pricing adjustments (with caching)
            BigDecimal adjustedPrice = applyDynamicPricing(basePrice, checkIn, checkOut, roomTypeId);
            
            log.info("Total price calculated: {} for {} nights (base: {}, adjusted: {})", 
                adjustedPrice, numberOfNights, basePrice, adjustedPrice);
            
            return adjustedPrice;
            
        } catch (Exception e) {
            log.error("Error calculating price for roomType: {}", roomTypeId, e);
            throw new ServiceCommunicationException("Unable to calculate pricing: " + e.getMessage(), e);
        }
    }
    
    // New async method for future controller updates
    public Mono<BigDecimal> calculateTotalPriceAsync(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        log.info("Calculating total price async for roomType {} from {} to {}", roomTypeId, checkIn, checkOut);
        
        return getRoomTypePriceAsync(roomTypeId)
            .map(pricePerNight -> {
                long numberOfNights = ChronoUnit.DAYS.between(checkIn, checkOut);
                BigDecimal basePrice = pricePerNight.multiply(BigDecimal.valueOf(numberOfNights));
                BigDecimal adjustedPrice = applyDynamicPricing(basePrice, checkIn, checkOut, roomTypeId);
                
                log.info("Total price calculated async: {} for {} nights", adjustedPrice, numberOfNights);
                return adjustedPrice;
            })
            .onErrorMap(e -> {
                log.error("Error calculating price async for roomType: {}", roomTypeId, e);
                return new ServiceCommunicationException("Unable to calculate pricing: " + e.getMessage(), e);
            });
    }
    
    @CircuitBreaker(name = "hotel-service", fallbackMethod = "getRoomTypePriceFallback")
    @Retry(name = "hotel-service")
    @TimeLimiter(name = "hotel-service")
    @Cacheable(value = "room-prices", key = "#roomTypeId", unless = "#result == null")
    public Mono<BigDecimal> getRoomTypePriceAsync(UUID roomTypeId) {
        log.debug("Fetching room type price for: {}", roomTypeId);
        
        WebClient webClient = webClientBuilder
            .baseUrl("http://hotel-service:8082")
            .build();
        
        return webClient.get()
            .uri("/api/v1/hotels/rooms/{id}", roomTypeId)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                log.warn("Room type not found: {}", roomTypeId);
                return Mono.error(new ServiceCommunicationException("Room type not found: " + roomTypeId));
            })
            .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                log.error("Hotel service unavailable for room type: {}", roomTypeId);
                return Mono.error(new ServiceCommunicationException("Hotel service unavailable"));
            })
            .bodyToMono(RoomTypeResponse.class)
            .map(response -> {
                if (response != null && response.getPricePerNight() != null) {
                    log.debug("Retrieved price {} for room type {}", response.getPricePerNight(), roomTypeId);
                    return response.getPricePerNight();
                }
                log.warn("Invalid response for room type: {}, using default price", roomTypeId);
                return getDefaultPrice();
            })
            .onErrorReturn(ServiceCommunicationException.class, getDefaultPrice())
            .doOnError(e -> log.error("Error fetching room type price for: {}", roomTypeId, e));
    }
    
    // Fallback method for circuit breaker
    public Mono<BigDecimal> getRoomTypePriceFallback(UUID roomTypeId, Exception ex) {
        log.warn("Hotel service circuit breaker activated for room type {}, using fallback price. Error: {}", 
                roomTypeId, ex.getMessage());
        return Mono.just(getDefaultPrice());
    }
    
    // Legacy sync method for backward compatibility (deprecated)
    @Deprecated
    @Cacheable(value = "room-prices", key = "#roomTypeId", unless = "#result == null")
    private BigDecimal getRoomTypePrice(UUID roomTypeId) {
        try {
            WebClient webClient = webClientBuilder
                .baseUrl("http://hotel-service:8082")
                .build();
            
            log.debug("Calling hotel service for room type price (sync - deprecated): {}", roomTypeId);
            
            // Use the async method and block for backward compatibility
            return getRoomTypePriceAsync(roomTypeId)
                .timeout(Duration.ofSeconds(5))
                .block();
            
        } catch (Exception e) {
            log.error("Error in legacy sync method for room type: {}", roomTypeId, e);
            return getDefaultPrice();
        }
    }
    
    private BigDecimal getDefaultPrice() {
        log.warn("Using default price as fallback");
        return BigDecimal.valueOf(100.00); // Default $100 per night
    }
    
    private BigDecimal applyDynamicPricing(BigDecimal basePrice, LocalDate checkIn, LocalDate checkOut, UUID roomTypeId) {
        // Use cached pricing multiplier for better performance
        BigDecimal pricingMultiplier = getCachedPricingMultiplier(checkIn, checkOut, roomTypeId);
        BigDecimal adjustedPrice = basePrice.multiply(pricingMultiplier);
        
        log.debug("Applied dynamic pricing: base={}, multiplier={}, final={}", 
                basePrice, pricingMultiplier, adjustedPrice);
        
        return adjustedPrice;
    }
    
    @Cacheable(value = "pricing-multipliers", 
               key = "#checkIn + '_' + #checkOut + '_' + #roomTypeId",
               unless = "#result == null")
    private BigDecimal getCachedPricingMultiplier(LocalDate checkIn, LocalDate checkOut, UUID roomTypeId) {
        log.debug("Calculating pricing multiplier for {} to {} (room type: {})", checkIn, checkOut, roomTypeId);
        
        BigDecimal multiplier = BigDecimal.ONE;
        
        // Weekend premium (Friday-Saturday nights)
        multiplier = multiplier.multiply(getWeekendMultiplier(checkIn, checkOut));
        
        // Seasonal adjustments
        multiplier = multiplier.multiply(getSeasonalMultiplier(checkIn, checkOut));
        
        // Advance booking discount
        multiplier = multiplier.multiply(getAdvanceBookingMultiplier(checkIn));
        
        // Future: Occupancy-based pricing (would require real-time occupancy data)
        // multiplier = multiplier.multiply(getOccupancyMultiplier(roomTypeId, checkIn, checkOut));
        
        log.debug("Calculated pricing multiplier: {} (cached)", multiplier);
        return multiplier;
    }
    
    private BigDecimal getWeekendMultiplier(LocalDate checkIn, LocalDate checkOut) {
        BigDecimal weekendPremium = BigDecimal.valueOf(1.20); // 20% premium
        
        LocalDate current = checkIn;
        int weekendNights = 0;
        int totalNights = 0;
        
        while (current.isBefore(checkOut)) {
            totalNights++;
            if (current.getDayOfWeek().getValue() >= 5) { // Friday = 5, Saturday = 6
                weekendNights++;
            }
            current = current.plusDays(1);
        }
        
        if (weekendNights == 0 || totalNights == 0) {
            return BigDecimal.ONE;
        }
        
        // Calculate weighted multiplier
        BigDecimal weekendWeight = BigDecimal.valueOf(weekendNights).divide(BigDecimal.valueOf(totalNights), 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal weekdayWeight = BigDecimal.ONE.subtract(weekendWeight);
        
        BigDecimal multiplier = weekdayWeight.add(weekendWeight.multiply(weekendPremium));
        
        log.debug("Weekend multiplier: {} (weekend nights: {}/{})", multiplier, weekendNights, totalNights);
        return multiplier;
    }
    
    private BigDecimal getSeasonalMultiplier(LocalDate checkIn, LocalDate checkOut) {
        int month = checkIn.getMonthValue();
        
        BigDecimal seasonalMultiplier = BigDecimal.ONE;
        
        if (month >= 6 && month <= 8) { // Summer
            seasonalMultiplier = BigDecimal.valueOf(1.15);
            log.debug("Applied summer season multiplier: 1.15");
        } else if (month == 12 || month <= 2) { // Winter holidays
            seasonalMultiplier = BigDecimal.valueOf(1.25);
            log.debug("Applied winter holiday multiplier: 1.25");
        }
        
        return seasonalMultiplier;
    }
    
    private BigDecimal getAdvanceBookingMultiplier(LocalDate checkIn) {
        long daysInAdvance = ChronoUnit.DAYS.between(LocalDate.now(), checkIn);
        
        if (daysInAdvance >= 30) {
            BigDecimal discount = BigDecimal.valueOf(0.90); // 10% discount
            log.debug("Applied advance booking multiplier: 0.90 for {} days advance", daysInAdvance);
            return discount;
        }
        
        return BigDecimal.ONE;
    }
    
    // Future implementation for occupancy-based pricing
    @SuppressWarnings("unused")
    private BigDecimal getOccupancyMultiplier(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        // This would require real-time occupancy data from inventory service
        // In real implementation, high occupancy would increase prices, low occupancy would decrease them
        
        log.debug("Occupancy-based pricing multiplier not implemented yet, returning 1.0");
        return BigDecimal.ONE;
    }
}