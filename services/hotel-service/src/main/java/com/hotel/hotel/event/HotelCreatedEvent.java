package com.hotel.hotel.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelCreatedEvent implements Serializable {
    private UUID hotelId;
    private String name;
    private String description;
    private String city;
    private String country;
    private String address;
    private Integer starRating;
    private List<String> amenities;
    private Double latitude;
    private Double longitude;
    private List<String> imageUrls;
}
