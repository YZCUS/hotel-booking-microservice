package com.hotel.booking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "room_inventory", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"room_type_id", "date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "room_type_id", nullable = false)
    private UUID roomTypeId;
    
    @Column(nullable = false)
    private LocalDate date;
    
    @Column(name = "available_rooms", nullable = false)
    private Integer availableRooms;
    
    @Version  // Optimistic locking for inventory management
    @Builder.Default
    private Integer version = 0;
}