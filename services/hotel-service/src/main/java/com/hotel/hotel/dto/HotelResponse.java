package com.hotel.hotel.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelResponse implements Serializable {
    private UUID id;
    private String name;
    private String description;
    private String address;
    private String city;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer starRating;
    private List<String> amenities;
    private List<RoomTypeResponse> roomTypes;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    // Additional computed fields
    private Long favoriteCount;
    private Boolean isFavorite;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}