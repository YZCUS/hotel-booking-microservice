package com.hotel.hotel.repository;

import com.hotel.hotel.entity.Hotel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, UUID>, JpaSpecificationExecutor<Hotel> {
    
    List<Hotel> findByCityIgnoreCase(String city);
    
    List<Hotel> findByCountryIgnoreCase(String country);
    
    List<Hotel> findByStarRatingGreaterThanEqual(Integer starRating);
    
    Page<Hotel> findByCityIgnoreCaseAndStarRatingGreaterThanEqual(
            String city, Integer starRating, Pageable pageable);
    
    @Query("SELECT h FROM Hotel h WHERE " +
           "(:city IS NULL OR LOWER(h.city) = LOWER(:city)) AND " +
           "(:country IS NULL OR LOWER(h.country) = LOWER(:country)) AND " +
           "(:minRating IS NULL OR h.starRating >= :minRating)")
    Page<Hotel> findHotelsWithCriteria(
            @Param("city") String city,
            @Param("country") String country,
            @Param("minRating") Integer minRating,
            Pageable pageable);
    
    @Query("SELECT h FROM Hotel h WHERE h.name ILIKE %:keyword% OR h.description ILIKE %:keyword%")
    List<Hotel> findByKeyword(@Param("keyword") String keyword);
    
    @Query("SELECT DISTINCT h.city FROM Hotel h WHERE h.city IS NOT NULL ORDER BY h.city")
    List<String> findAllCities();
    
    @Query("SELECT DISTINCT h.country FROM Hotel h WHERE h.country IS NOT NULL ORDER BY h.country")
    List<String> findAllCountries();
}