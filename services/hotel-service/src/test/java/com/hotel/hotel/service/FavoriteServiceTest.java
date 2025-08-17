package com.hotel.hotel.service;

import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.UserFavorite;
import com.hotel.hotel.exception.DuplicateFavoriteException;
import com.hotel.hotel.exception.FavoriteNotFoundException;
import com.hotel.hotel.exception.HotelNotFoundException;
import com.hotel.hotel.repository.HotelRepository;
import com.hotel.hotel.repository.UserFavoriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {
    
    @Mock
    private UserFavoriteRepository favoriteRepository;
    
    @Mock
    private HotelRepository hotelRepository;
    
    @Mock
    private HotelService hotelService;
    
    @InjectMocks
    private FavoriteService favoriteService;
    
    private UUID testUserId;
    private UUID testHotelId;
    private UserFavorite testFavorite;
    
    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testHotelId = UUID.randomUUID();
        
        testFavorite = UserFavorite.builder()
                .userId(testUserId)
                .hotelId(testHotelId)
                .createdAt(LocalDateTime.now())
                .build();
    }
    
    @Test
    void testAddFavorite_Success() {
        // Given
        when(hotelRepository.existsById(testHotelId)).thenReturn(true);
        when(favoriteRepository.existsByUserIdAndHotelId(testUserId, testHotelId)).thenReturn(false);
        when(favoriteRepository.save(any(UserFavorite.class))).thenReturn(testFavorite);
        
        // When
        favoriteService.addFavorite(testUserId, testHotelId);
        
        // Then
        verify(hotelRepository).existsById(testHotelId);
        verify(favoriteRepository).existsByUserIdAndHotelId(testUserId, testHotelId);
        verify(favoriteRepository).save(any(UserFavorite.class));
    }
    
    @Test
    void testAddFavorite_HotelNotFound() {
        // Given
        when(hotelRepository.existsById(testHotelId)).thenReturn(false);
        
        // When & Then
        assertThrows(HotelNotFoundException.class, () -> {
            favoriteService.addFavorite(testUserId, testHotelId);
        });
        
        verify(hotelRepository).existsById(testHotelId);
        verify(favoriteRepository, never()).existsByUserIdAndHotelId(any(), any());
        verify(favoriteRepository, never()).save(any());
    }
    
    @Test
    void testAddFavorite_DuplicateFavorite() {
        // Given
        when(hotelRepository.existsById(testHotelId)).thenReturn(true);
        when(favoriteRepository.existsByUserIdAndHotelId(testUserId, testHotelId)).thenReturn(true);
        
        // When & Then
        assertThrows(DuplicateFavoriteException.class, () -> {
            favoriteService.addFavorite(testUserId, testHotelId);
        });
        
        verify(hotelRepository).existsById(testHotelId);
        verify(favoriteRepository).existsByUserIdAndHotelId(testUserId, testHotelId);
        verify(favoriteRepository, never()).save(any());
    }
    
    @Test
    void testRemoveFavorite_Success() {
        // Given
        when(favoriteRepository.findByUserIdAndHotelId(testUserId, testHotelId))
                .thenReturn(Optional.of(testFavorite));
        
        // When
        favoriteService.removeFavorite(testUserId, testHotelId);
        
        // Then
        verify(favoriteRepository).findByUserIdAndHotelId(testUserId, testHotelId);
        verify(favoriteRepository).delete(testFavorite);
    }
    
    @Test
    void testRemoveFavorite_NotFound() {
        // Given
        when(favoriteRepository.findByUserIdAndHotelId(testUserId, testHotelId))
                .thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(FavoriteNotFoundException.class, () -> {
            favoriteService.removeFavorite(testUserId, testHotelId);
        });
        
        verify(favoriteRepository).findByUserIdAndHotelId(testUserId, testHotelId);
        verify(favoriteRepository, never()).delete(any());
    }
    
    @Test
    void testIsFavorite_True() {
        // Given
        when(favoriteRepository.existsByUserIdAndHotelId(testUserId, testHotelId)).thenReturn(true);
        
        // When
        boolean result = favoriteService.isFavorite(testUserId, testHotelId);
        
        // Then
        assertTrue(result);
        verify(favoriteRepository).existsByUserIdAndHotelId(testUserId, testHotelId);
    }
    
    @Test
    void testIsFavorite_False() {
        // Given
        when(favoriteRepository.existsByUserIdAndHotelId(testUserId, testHotelId)).thenReturn(false);
        
        // When
        boolean result = favoriteService.isFavorite(testUserId, testHotelId);
        
        // Then
        assertFalse(result);
        verify(favoriteRepository).existsByUserIdAndHotelId(testUserId, testHotelId);
    }
    
    @Test
    void testGetFavoriteCount() {
        // Given
        when(favoriteRepository.countFavoritesByHotelId(testHotelId)).thenReturn(5L);
        
        // When
        Long count = favoriteService.getFavoriteCount(testHotelId);
        
        // Then
        assertEquals(5L, count);
        verify(favoriteRepository).countFavoritesByHotelId(testHotelId);
    }
    
    @Test
    void testGetUserFavoriteCount() {
        // Given
        when(favoriteRepository.countFavoritesByUserId(testUserId)).thenReturn(3L);
        
        // When
        Long count = favoriteService.getUserFavoriteCount(testUserId);
        
        // Then
        assertEquals(3L, count);
        verify(favoriteRepository).countFavoritesByUserId(testUserId);
    }
    
    @Test
    void testRemoveAllUserFavorites() {
        // Given
        List<UserFavorite> favorites = Arrays.asList(testFavorite);
        when(favoriteRepository.findByUserId(testUserId)).thenReturn(favorites);
        
        // When
        favoriteService.removeAllUserFavorites(testUserId);
        
        // Then
        verify(favoriteRepository).findByUserId(testUserId);
        verify(favoriteRepository).deleteAll(favorites);
    }
    
    @Test
    void testRemoveAllUserFavorites_EmptyList() {
        // Given
        when(favoriteRepository.findByUserId(testUserId)).thenReturn(Arrays.asList());
        
        // When
        favoriteService.removeAllUserFavorites(testUserId);
        
        // Then
        verify(favoriteRepository).findByUserId(testUserId);
        verify(favoriteRepository, never()).deleteAll(any());
    }
}