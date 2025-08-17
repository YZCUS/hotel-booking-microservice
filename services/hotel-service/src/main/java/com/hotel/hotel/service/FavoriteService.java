package com.hotel.hotel.service;

import com.hotel.hotel.dto.HotelResponse;
import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.UserFavorite;
import com.hotel.hotel.exception.DuplicateFavoriteException;
import com.hotel.hotel.exception.FavoriteNotFoundException;
import com.hotel.hotel.exception.HotelNotFoundException;
import com.hotel.hotel.repository.HotelRepository;
import com.hotel.hotel.repository.UserFavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FavoriteService {
    
    private final UserFavoriteRepository favoriteRepository;
    private final HotelRepository hotelRepository;
    private final HotelService hotelService;
    
    public void addFavorite(UUID userId, UUID hotelId) {
        log.info("Adding hotel {} to favorites for user {}", hotelId, userId);
        
        // Check if hotel exists
        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException("Hotel not found with id: " + hotelId);
        }
        
        // Check if already favorited
        if (favoriteRepository.existsByUserIdAndHotelId(userId, hotelId)) {
            throw new DuplicateFavoriteException("Hotel is already in user's favorites");
        }
        
        UserFavorite favorite = UserFavorite.builder()
                .userId(userId)
                .hotelId(hotelId)
                .build();
        
        favoriteRepository.save(favorite);
        log.info("Successfully added hotel {} to favorites for user {}", hotelId, userId);
    }
    
    public List<HotelResponse> getUserFavorites(UUID userId) {
        log.info("Getting favorites for user: {}", userId);
        
        List<UserFavorite> favorites = favoriteRepository.findByUserId(userId);
        
        if (favorites.isEmpty()) {
            return List.of();
        }
        
        List<UUID> hotelIds = favorites.stream()
                .map(UserFavorite::getHotelId)
                .collect(Collectors.toList());
        
        List<Hotel> hotels = hotelRepository.findAllById(hotelIds);
        
        return hotels.stream()
                .map(hotel -> mapToResponse(hotel, userId))
                .collect(Collectors.toList());
    }
    
    public void removeFavorite(UUID userId, UUID hotelId) {
        log.info("Removing hotel {} from favorites for user {}", hotelId, userId);
        
        UserFavorite favorite = favoriteRepository.findByUserIdAndHotelId(userId, hotelId)
                .orElseThrow(() -> new FavoriteNotFoundException("Favorite not found"));
        
        favoriteRepository.delete(favorite);
        log.info("Successfully removed hotel {} from favorites for user {}", hotelId, userId);
    }
    
    public boolean isFavorite(UUID userId, UUID hotelId) {
        return favoriteRepository.existsByUserIdAndHotelId(userId, hotelId);
    }
    
    public Long getFavoriteCount(UUID hotelId) {
        return favoriteRepository.countFavoritesByHotelId(hotelId);
    }
    
    public Long getUserFavoriteCount(UUID userId) {
        return favoriteRepository.countFavoritesByUserId(userId);
    }
    
    public void removeAllUserFavorites(UUID userId) {
        log.info("Removing all favorites for user: {}", userId);
        
        List<UserFavorite> favorites = favoriteRepository.findByUserId(userId);
        if (!favorites.isEmpty()) {
            favoriteRepository.deleteAll(favorites);
            log.info("Removed {} favorites for user {}", favorites.size(), userId);
        }
    }
    
    private HotelResponse mapToResponse(Hotel hotel, UUID userId) {
        // Use the hotel service to get the full response with all computed fields
        return hotelService.getHotelById(hotel.getId(), userId);
    }
}