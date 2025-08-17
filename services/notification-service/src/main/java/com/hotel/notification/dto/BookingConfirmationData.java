package com.hotel.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmationData {
    
    private UUID bookingId;
    private String userName;
    private String userEmail;
    private String hotelName;
    private String hotelAddress;
    private String roomTypeName;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer guests;
    private BigDecimal totalPrice;
    private LocalDateTime bookingTime;
    private String confirmationNumber;
    private String hotelPhone;
    private String hotelEmail;
    private String cancellationPolicy;
}