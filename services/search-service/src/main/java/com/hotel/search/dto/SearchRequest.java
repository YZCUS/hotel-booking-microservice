package com.hotel.search.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {
    
    private String query;
    
    private String city;
    
    private String country;
    
    private Integer minRating;
    
    private Integer maxRating;
    
    private BigDecimal minPrice;
    
    private BigDecimal maxPrice;
    
    private List<String> amenities;
    
    private LocalDate checkInDate;
    
    private LocalDate checkOutDate;
    
    private Integer guests;
    
    @Min(0)
    @Builder.Default
    private Integer offset = 0;
    
    @Min(1)
    @Builder.Default
    private Integer limit = 20;
    
    private String sortBy;
    
    @Builder.Default
    private String sortOrder = "asc";
    
    private Double latitude;
    
    private Double longitude;
    
    private Double radiusKm;
}