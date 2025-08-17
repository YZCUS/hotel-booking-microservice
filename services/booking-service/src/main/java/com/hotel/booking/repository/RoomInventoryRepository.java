package com.hotel.booking.repository;

import com.hotel.booking.entity.RoomInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomInventoryRepository extends JpaRepository<RoomInventory, UUID> {
    
    Optional<RoomInventory> findByRoomTypeIdAndDate(UUID roomTypeId, LocalDate date);
    
    List<RoomInventory> findByRoomTypeIdAndDateBetween(
            UUID roomTypeId, LocalDate startDate, LocalDate endDate);
    
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT ri FROM RoomInventory ri WHERE ri.roomTypeId = :roomTypeId AND ri.date = :date")
    Optional<RoomInventory> findByRoomTypeIdAndDateWithLock(
            @Param("roomTypeId") UUID roomTypeId, 
            @Param("date") LocalDate date);
    
    @Query("SELECT ri FROM RoomInventory ri WHERE ri.roomTypeId = :roomTypeId " +
           "AND ri.date BETWEEN :startDate AND :endDate " +
           "AND ri.availableRooms >= :requiredRooms")
    List<RoomInventory> findAvailableInventory(
            @Param("roomTypeId") UUID roomTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("requiredRooms") Integer requiredRooms);
    
    @Query("SELECT MIN(ri.availableRooms) FROM RoomInventory ri " +
           "WHERE ri.roomTypeId = :roomTypeId " +
           "AND ri.date BETWEEN :startDate AND :endDate")
    Optional<Integer> findMinAvailableRoomsInRange(
            @Param("roomTypeId") UUID roomTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    
    @Query("SELECT COUNT(ri) FROM RoomInventory ri " +
           "WHERE ri.roomTypeId = :roomTypeId " +
           "AND ri.date BETWEEN :startDate AND :endDate " +
           "AND ri.availableRooms < :requiredRooms")
    Long countUnavailableDates(
            @Param("roomTypeId") UUID roomTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("requiredRooms") Integer requiredRooms);
    
    void deleteByRoomTypeId(UUID roomTypeId);
}