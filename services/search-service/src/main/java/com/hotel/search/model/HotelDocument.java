package com.hotel.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelDocument {
    
    @JsonProperty("id")
    private UUID id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("city")
    private String city;
    
    @JsonProperty("country")
    private String country;
    
    @JsonProperty("address")
    private String address;
    
    @JsonProperty("starRating")
    private Integer starRating;
    
    @JsonProperty("minPrice")
    private BigDecimal minPrice;
    
    @JsonProperty("maxPrice")
    private BigDecimal maxPrice;
    
    @JsonProperty("amenities")
    private List<String> amenities;
    
    @JsonProperty("latitude")
    private Double latitude;
    
    @JsonProperty("longitude")
    private Double longitude;
    
    @JsonProperty("imageUrls")
    private List<String> imageUrls;
    
    @JsonProperty("totalRooms")
    private Integer totalRooms;
    
    @JsonProperty("availableRooms")
    private Integer availableRooms;
    
    @JsonProperty("averageRating")
    private Double averageRating;
    
    @JsonProperty("reviewCount")
    private Integer reviewCount;
    
    @JsonProperty("isActive")
    private Boolean isActive;
}