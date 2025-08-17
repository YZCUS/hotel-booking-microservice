package com.hotel.booking.repository;

import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    
    Page<Booking> findByUserId(UUID userId, Pageable pageable);
    
    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);
    
    List<Booking> findByUserIdAndStatus(UUID userId, BookingStatus status);
    
    List<Booking> findByRoomTypeIdAndCheckInDateBetween(
            UUID roomTypeId, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT b FROM Booking b WHERE b.userId = :userId " +
           "AND b.status IN :statuses " +
           "ORDER BY b.createdAt DESC")
    List<Booking> findByUserIdAndStatusIn(
            @Param("userId") UUID userId, 
            @Param("statuses") List<BookingStatus> statuses);
    
    @Query("SELECT b FROM Booking b WHERE b.checkInDate = :date " +
           "AND b.status = 'CONFIRMED'")
    List<Booking> findTodaysCheckIns(@Param("date") LocalDate date);
    
    @Query("SELECT b FROM Booking b WHERE b.checkOutDate = :date " +
           "AND b.status = 'CHECKED_IN'")
    List<Booking> findTodaysCheckOuts(@Param("date") LocalDate date);
    
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.roomTypeId = :roomTypeId " +
           "AND b.checkInDate <= :checkOut AND b.checkOutDate > :checkIn " +
           "AND b.status IN ('CONFIRMED', 'CHECKED_IN')")
    Long countOverlappingBookings(
            @Param("roomTypeId") UUID roomTypeId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut);
    
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.userId = :userId " +
           "AND b.status = :status")
    Long countByUserIdAndStatus(
            @Param("userId") UUID userId, 
            @Param("status") BookingStatus status);
}