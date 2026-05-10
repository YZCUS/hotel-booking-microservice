package com.hotel.user.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegisteredEvent implements Serializable {
    private UUID userId;
    private String email;
    private String fullName;
    private LocalDateTime registeredAt;
}
