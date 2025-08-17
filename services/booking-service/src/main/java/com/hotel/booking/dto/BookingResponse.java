package com.hotel.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hotel.booking.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponse implements Serializable {
    private UUID id;
    private UUID userId;
    private UUID roomTypeId;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer guests;
    private BigDecimal totalPrice;
    private BookingStatus status;
    private String roomNumber;
    private Integer version;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    // Additional computed fields
    private Integer numberOfNights;
    private Boolean canCancel;
    private Boolean canCheckIn;
    private String hotelName;
    private String roomTypeName;
}