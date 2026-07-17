package com.hotel.booking.service;

import com.hotel.booking.dto.RoomTypeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {
    
    private final HotelCatalogClient hotelCatalogClient;
    
    public BigDecimal calculateTotalPrice(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        return calculateTotalPrice(hotelCatalogClient.getRoomType(roomTypeId), checkIn, checkOut);
    }

    public BigDecimal calculateTotalPrice(RoomTypeResponse roomType, LocalDate checkIn, LocalDate checkOut) {
        long numberOfNights = ChronoUnit.DAYS.between(checkIn, checkOut);
        BigDecimal basePrice = roomType.getPricePerNight().multiply(BigDecimal.valueOf(numberOfNights));
        return applyDynamicPricing(basePrice, checkIn, checkOut, roomType.getId());
    }

    public Mono<BigDecimal> calculateTotalPriceAsync(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        return Mono.fromCallable(() -> calculateTotalPrice(roomTypeId, checkIn, checkOut));
    }
    
    private BigDecimal applyDynamicPricing(BigDecimal basePrice, LocalDate checkIn, LocalDate checkOut, UUID roomTypeId) {
        // Use cached pricing multiplier for better performance
        BigDecimal pricingMultiplier = getCachedPricingMultiplier(checkIn, checkOut, roomTypeId);
        BigDecimal adjustedPrice = basePrice.multiply(pricingMultiplier);
        
        log.debug("Applied dynamic pricing: base={}, multiplier={}, final={}", 
                basePrice, pricingMultiplier, adjustedPrice);
        
        return adjustedPrice;
    }
    
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
