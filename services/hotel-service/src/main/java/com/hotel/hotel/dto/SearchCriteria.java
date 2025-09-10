package com.hotel.hotel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchCriteria {
    private String city;
    private String country;
    private Integer minRating;
    private Integer maxRating;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer minCapacity;
    private List<String> amenities;
    private String keyword;
    
    // For availability checking
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer guests;
    
    // Geolocation search
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Double radiusKm;

}