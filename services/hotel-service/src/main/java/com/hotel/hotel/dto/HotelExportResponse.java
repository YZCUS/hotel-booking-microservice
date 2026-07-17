package com.hotel.hotel.dto;

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
public class HotelExportResponse {
    private UUID id;
    private String name;
    private String description;
    private String city;
    private String country;
    private String address;
    private Integer starRating;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<String> amenities;
    private Double latitude;
    private Double longitude;
    private List<String> imageUrls;
    private Integer totalRooms;
    private Integer availableRooms;
    private Double averageRating;
    private Integer reviewCount;
    private Boolean isActive;
    private List<RoomTypeResponse> roomTypes;
}
