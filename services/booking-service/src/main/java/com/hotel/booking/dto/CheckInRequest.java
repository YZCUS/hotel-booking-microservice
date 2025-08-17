package com.hotel.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInRequest {
    @NotBlank(message = "Room number is required")
    @Pattern(regexp = "^[A-Z0-9]{2,10}$", message = "Room number must be 2-10 alphanumeric characters")
    private String roomNumber;
    
    private String specialRequests;
}