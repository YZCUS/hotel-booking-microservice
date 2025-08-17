package com.hotel.hotel.repository;

import com.hotel.hotel.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {
    
    List<RoomType> findByHotelId(UUID hotelId);
    
    List<RoomType> findByHotelIdAndCapacityGreaterThanEqual(UUID hotelId, Integer capacity);
    
    @Query("SELECT rt FROM RoomType rt WHERE rt.hotel.id = :hotelId AND rt.pricePerNight BETWEEN :minPrice AND :maxPrice")
    List<RoomType> findByHotelIdAndPriceRange(
            @Param("hotelId") UUID hotelId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice);
    
    @Query("SELECT rt FROM RoomType rt WHERE rt.hotel.id = :hotelId ORDER BY rt.pricePerNight ASC")
    List<RoomType> findByHotelIdOrderByPriceAsc(@Param("hotelId") UUID hotelId);
    
    @Query("SELECT COUNT(rt) FROM RoomType rt WHERE rt.hotel.id = :hotelId")
    Long countByHotelId(@Param("hotelId") UUID hotelId);
}