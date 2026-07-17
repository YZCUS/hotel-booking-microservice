package com.hotel.hotel.repository;

import com.hotel.hotel.entity.UserFavorite;
import com.hotel.hotel.entity.UserFavoriteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UserFavoriteId> {
    
    List<UserFavorite> findByUserId(UUID userId);
    
    List<UserFavorite> findByHotelId(UUID hotelId);
    
    Optional<UserFavorite> findByUserIdAndHotelId(UUID userId, UUID hotelId);
    
    boolean existsByUserIdAndHotelId(UUID userId, UUID hotelId);
    
    void deleteByUserIdAndHotelId(UUID userId, UUID hotelId);
    
    @Query("SELECT COUNT(uf) FROM UserFavorite uf WHERE uf.hotelId = :hotelId")
    Long countFavoritesByHotelId(@Param("hotelId") UUID hotelId);

    @Query("SELECT uf.hotelId, COUNT(uf) FROM UserFavorite uf WHERE uf.hotelId IN :hotelIds GROUP BY uf.hotelId")
    List<Object[]> countFavoritesByHotelIds(@Param("hotelIds") Collection<UUID> hotelIds);

    @Query("SELECT uf.hotelId FROM UserFavorite uf WHERE uf.userId = :userId AND uf.hotelId IN :hotelIds")
    List<UUID> findFavoriteHotelIdsByUserIdAndHotelIdIn(
            @Param("userId") UUID userId,
            @Param("hotelIds") Collection<UUID> hotelIds);
    
    @Query("SELECT COUNT(uf) FROM UserFavorite uf WHERE uf.userId = :userId")
    Long countFavoritesByUserId(@Param("userId") UUID userId);
}
