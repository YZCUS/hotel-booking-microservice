package com.hotel.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {
    
    private final RestTemplate restTemplate;
    
    public BigDecimal calculateTotalPrice(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        log.info("Calculating total price for roomType {} from {} to {}", roomTypeId, checkIn, checkOut);
        
        try {
            // Get room type price from hotel service
            BigDecimal pricePerNight = getRoomTypePrice(roomTypeId);
            
            // Calculate number of nights
            long numberOfNights = ChronoUnit.DAYS.between(checkIn, checkOut);
            
            // Calculate base price
            BigDecimal basePrice = pricePerNight.multiply(BigDecimal.valueOf(numberOfNights));
            
            // Apply dynamic pricing adjustments
            BigDecimal adjustedPrice = applyDynamicPricing(basePrice, checkIn, checkOut, roomTypeId);
            
            log.info("Total price calculated: {} for {} nights (base: {}, adjusted: {})", 
                adjustedPrice, numberOfNights, basePrice, adjustedPrice);
            
            return adjustedPrice;
            
        } catch (Exception e) {
            log.error("Error calculating price for roomType: {}", roomTypeId, e);
            throw new RuntimeException("Unable to calculate pricing", e);
        }
    }
    
    private BigDecimal getRoomTypePrice(UUID roomTypeId) {
        try {
            // Call hotel service to get room type details
            String url = "http://hotel-service:8082/api/v1/hotels/rooms/" + roomTypeId;
            
            // For now, return a default price until hotel service integration is complete
            // In real implementation, this would make an HTTP call to hotel service
            log.debug("Would call hotel service for room type price: {}", roomTypeId);
            
            // Default pricing logic - this should be replaced with actual service call
            return BigDecimal.valueOf(100.00); // Default $100 per night
            
        } catch (Exception e) {
            log.error("Error fetching room type price for: {}", roomTypeId, e);
            // Return default price as fallback
            return BigDecimal.valueOf(100.00);
        }
    }
    
    private BigDecimal applyDynamicPricing(BigDecimal basePrice, LocalDate checkIn, LocalDate checkOut, UUID roomTypeId) {
        BigDecimal adjustedPrice = basePrice;
        
        // Weekend premium (Friday-Saturday nights)
        adjustedPrice = applyWeekendPremium(adjustedPrice, checkIn, checkOut);
        
        // Seasonal adjustments
        adjustedPrice = applySeasonalAdjustments(adjustedPrice, checkIn, checkOut);
        
        // Advance booking discount
        adjustedPrice = applyAdvanceBookingDiscount(adjustedPrice, checkIn);
        
        // Occupancy-based pricing (would require real-time occupancy data)
        adjustedPrice = applyOccupancyPricing(adjustedPrice, roomTypeId, checkIn, checkOut);
        
        return adjustedPrice;
    }
    
    private BigDecimal applyWeekendPremium(BigDecimal price, LocalDate checkIn, LocalDate checkOut) {
        // Apply 20% premium for weekend nights (Friday and Saturday)
        BigDecimal premiumRate = BigDecimal.valueOf(1.20);
        
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
        
        if (weekendNights > 0) {
            BigDecimal weekendPrice = price.divide(BigDecimal.valueOf(totalNights))
                .multiply(BigDecimal.valueOf(weekendNights))
                .multiply(premiumRate);
            BigDecimal weekdayPrice = price.divide(BigDecimal.valueOf(totalNights))
                .multiply(BigDecimal.valueOf(totalNights - weekendNights));
            
            price = weekendPrice.add(weekdayPrice);
            log.debug("Applied weekend premium: {} weekend nights out of {}", weekendNights, totalNights);
        }
        
        return price;
    }
    
    private BigDecimal applySeasonalAdjustments(BigDecimal price, LocalDate checkIn, LocalDate checkOut) {
        // Simple seasonal pricing - summer and winter holidays get premium
        int month = checkIn.getMonthValue();
        
        BigDecimal seasonalMultiplier = BigDecimal.ONE;
        
        if (month >= 6 && month <= 8) { // Summer
            seasonalMultiplier = BigDecimal.valueOf(1.15);
            log.debug("Applied summer season premium: 15%");
        } else if (month == 12 || month <= 2) { // Winter holidays
            seasonalMultiplier = BigDecimal.valueOf(1.25);
            log.debug("Applied winter holiday premium: 25%");
        }
        
        return price.multiply(seasonalMultiplier);
    }
    
    private BigDecimal applyAdvanceBookingDiscount(BigDecimal price, LocalDate checkIn) {
        // Discount for advance bookings (30+ days ahead)
        long daysInAdvance = ChronoUnit.DAYS.between(LocalDate.now(), checkIn);
        
        if (daysInAdvance >= 30) {
            BigDecimal discount = BigDecimal.valueOf(0.90); // 10% discount
            price = price.multiply(discount);
            log.debug("Applied advance booking discount: 10% for {} days advance", daysInAdvance);
        }
        
        return price;
    }
    
    private BigDecimal applyOccupancyPricing(BigDecimal price, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        // This would require real-time occupancy data from inventory service
        // For now, just return the price unchanged
        // In real implementation, high occupancy would increase prices, low occupancy would decrease them
        
        log.debug("Occupancy-based pricing not implemented yet");
        return price;
    }
}