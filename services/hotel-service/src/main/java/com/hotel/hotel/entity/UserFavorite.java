package com.hotel.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_favorites")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(UserFavoriteId.class)
public class UserFavorite {
    @Id
    @Column(name = "user_id")
    private UUID userId;
    
    @Id
    @Column(name = "hotel_id")
    private UUID hotelId;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}