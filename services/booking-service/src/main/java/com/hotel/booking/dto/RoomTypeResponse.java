package com.hotel.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeResponse implements Serializable {
    private UUID id;
    private UUID hotelId;
    private String hotelName;
    private String name;
    private String description;
    private Integer capacity;
    private BigDecimal pricePerNight;
    private Integer totalInventory;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    // Additional computed fields
    private Integer availableRooms;
    private Boolean isAvailable;
}