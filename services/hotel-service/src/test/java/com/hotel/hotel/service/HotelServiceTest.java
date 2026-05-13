package com.hotel.hotel.service;

import com.hotel.hotel.dto.HotelRequest;
import com.hotel.hotel.dto.HotelResponse;
import com.hotel.hotel.dto.RoomTypeResponse;
import com.hotel.hotel.dto.SearchCriteria;
import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.RoomType;
import com.hotel.hotel.exception.HotelNotFoundException;
import com.hotel.hotel.repository.HotelRepository;
import com.hotel.hotel.repository.RoomTypeRepository;
import com.hotel.hotel.repository.UserFavoriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotelServiceTest {
    
    @Mock
    private HotelRepository hotelRepository;
    
    @Mock
    private UserFavoriteRepository favoriteRepository;
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RoomService roomService;

    @Mock
    private RoomTypeRepository roomTypeRepository;
    
    @InjectMocks
    private HotelService hotelService;
    
    private Hotel testHotel;
    private UUID testHotelId;
    private UUID testUserId;
    
    @BeforeEach
    void setUp() {
        testHotelId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        
        testHotel = Hotel.builder()
                .id(testHotelId)
                .name("Test Hotel")
                .description("A test hotel")
                .address("123 Test St")
                .city("Test City")
                .country("Test Country")
                .latitude(new BigDecimal("40.7128"))
                .longitude(new BigDecimal("-74.0060"))
                .starRating(4)
                .amenities(Arrays.asList("WiFi", "Pool"))
                .createdAt(LocalDateTime.now())
                .build();

        lenient().when(roomService.mapToResponse(any(RoomType.class), anyInt())).thenAnswer(invocation -> {
            RoomType roomType = invocation.getArgument(0);
            Integer availableRooms = invocation.getArgument(1);
            return RoomTypeResponse.builder()
                    .id(roomType.getId())
                    .hotelId(roomType.getHotel().getId())
                    .hotelName(roomType.getHotel().getName())
                    .name(roomType.getName())
                    .description(roomType.getDescription())
                    .capacity(roomType.getCapacity())
                    .pricePerNight(roomType.getPricePerNight())
                    .totalInventory(roomType.getTotalInventory())
                    .createdAt(roomType.getCreatedAt())
                    .availableRooms(availableRooms)
                    .isAvailable(availableRooms != null && availableRooms > 0)
                    .build();
        });
    }
    
    @Test
    void testGetHotelById_Success() {
        // Given
        stubRoomAvailability();
        when(hotelRepository.findById(testHotelId)).thenReturn(Optional.of(testHotel));
        when(favoriteRepository.countFavoritesByHotelId(testHotelId)).thenReturn(5L);
        
        // When
        HotelResponse response = hotelService.getHotelById(testHotelId);
        
        // Then
        assertNotNull(response);
        assertEquals(testHotel.getId(), response.getId());
        assertEquals(testHotel.getName(), response.getName());
        assertEquals(testHotel.getCity(), response.getCity());
        assertEquals(testHotel.getStarRating(), response.getStarRating());
        assertEquals(5L, response.getFavoriteCount());
        assertNull(response.getIsFavorite()); // No user ID provided
        
        verify(hotelRepository).findById(testHotelId);
        verify(favoriteRepository).countFavoritesByHotelId(testHotelId);
    }
    
    @Test
    void testGetHotelById_WithUserId() {
        // Given
        stubRoomAvailability();
        when(hotelRepository.findById(testHotelId)).thenReturn(Optional.of(testHotel));
        when(favoriteRepository.countFavoritesByHotelId(testHotelId)).thenReturn(3L);
        when(favoriteRepository.existsByUserIdAndHotelId(testUserId, testHotelId)).thenReturn(true);
        
        // When
        HotelResponse response = hotelService.getHotelById(testHotelId, testUserId);
        
        // Then
        assertNotNull(response);
        assertEquals(testHotel.getId(), response.getId());
        assertEquals(3L, response.getFavoriteCount());
        assertTrue(response.getIsFavorite());
        
        verify(hotelRepository).findById(testHotelId);
        verify(favoriteRepository).countFavoritesByHotelId(testHotelId);
        verify(favoriteRepository).existsByUserIdAndHotelId(testUserId, testHotelId);
    }
    
    @Test
    void testGetHotelById_NotFound() {
        // Given
        when(hotelRepository.findById(testHotelId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(HotelNotFoundException.class, () -> {
            hotelService.getHotelById(testHotelId);
        });
        
        verify(hotelRepository).findById(testHotelId);
    }
    
    @Test
    void testSearchHotels_WithCityFilter() {
        // Given
        stubSearchCache();
        stubRoomAvailability();
        SearchCriteria criteria = SearchCriteria.builder()
                .city("Test City")
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        
        Page<Hotel> hotelPage = new PageImpl<>(Arrays.asList(testHotel));
        
        when(valueOperations.get(anyString())).thenReturn(null);
        when(hotelRepository.findAll(ArgumentMatchers.<Specification<Hotel>>any(), any(Pageable.class))).thenReturn(hotelPage);
        when(roomTypeRepository.findByHotelIdIn(any())).thenReturn(List.of());
        
        // When
        Page<HotelResponse> result = hotelService.searchHotels(criteria, pageable);
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Test Hotel", result.getContent().get(0).getName());
        assertEquals("Test City", result.getContent().get(0).getCity());
        
        verify(hotelRepository).findAll(ArgumentMatchers.<Specification<Hotel>>any(), any(Pageable.class));
        verify(valueOperations).set(anyString(), any(), any(Long.class), any(TimeUnit.class));
    }

    @Test
    void testSearchHotels_BatchesAvailabilityLookupForPage() {
        stubSearchCache();
        SearchCriteria criteria = SearchCriteria.builder().city("Test City").build();
        Pageable pageable = PageRequest.of(0, 20);
        UUID secondHotelId = UUID.randomUUID();
        UUID firstRoomTypeId = UUID.randomUUID();
        UUID secondRoomTypeId = UUID.randomUUID();
        RoomType firstRoom = roomType(firstRoomTypeId, testHotel, "Deluxe");
        testHotel.setRoomTypes(List.of(firstRoom));
        Hotel secondHotel = Hotel.builder()
                .id(secondHotelId)
                .name("Second Hotel")
                .city("Test City")
                .country("Test Country")
                .roomTypes(List.of())
                .createdAt(LocalDateTime.now())
                .build();
        RoomType secondRoom = roomType(secondRoomTypeId, secondHotel, "Standard");
        secondHotel.setRoomTypes(List.of(secondRoom));

        when(valueOperations.get(anyString())).thenReturn(null);
        when(hotelRepository.findAll(ArgumentMatchers.<Specification<Hotel>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testHotel, secondHotel)));
        when(roomTypeRepository.findByHotelIdIn(any())).thenReturn(List.of(firstRoom, secondRoom));
        when(roomService.getRoomAvailabilities(anyList())).thenReturn(Map.of(
                firstRoomTypeId, 4,
                secondRoomTypeId, 7));

        Page<HotelResponse> result = hotelService.searchHotels(criteria, pageable);

        assertEquals(2, result.getContent().size());
        assertEquals(4, result.getContent().get(0).getRoomTypes().getFirst().getAvailableRooms());
        assertEquals(7, result.getContent().get(1).getRoomTypes().getFirst().getAvailableRooms());
        verify(roomService, times(1)).getRoomAvailabilities(argThat(ids ->
                ids.size() == 2 && ids.contains(firstRoomTypeId) && ids.contains(secondRoomTypeId)));
    }
    
    @Test
    void testSearchHotels_FromCache() {
        // Given
        stubSearchCache();
        SearchCriteria criteria = SearchCriteria.builder()
                .city("Test City")
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        
        HotelResponse cachedHotel = HotelResponse.builder()
                .id(testHotelId)
                .name("Cached Hotel")
                .city("Test City")
                .build();
        Page<HotelResponse> cachedPage = new PageImpl<>(Arrays.asList(cachedHotel));
        
        when(valueOperations.get(anyString())).thenReturn(cachedPage);
        
        // When
        Page<HotelResponse> result = hotelService.searchHotels(criteria, pageable);
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Cached Hotel", result.getContent().get(0).getName());
        
        verify(hotelRepository, never()).findAll(ArgumentMatchers.<Specification<Hotel>>any(), any(Pageable.class));
    }
    
    @Test
    void testCreateHotel_Success() {
        // Given
        stubRoomAvailability();
        stubSearchCacheInvalidation();
        HotelRequest request = HotelRequest.builder()
                .name("New Hotel")
                .description("A new hotel")
                .city("New City")
                .country("New Country")
                .starRating(5)
                .amenities(Arrays.asList("WiFi", "Spa"))
                .build();
        
        Hotel savedHotel = Hotel.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .description(request.getDescription())
                .city(request.getCity())
                .country(request.getCountry())
                .starRating(request.getStarRating())
                .amenities(request.getAmenities())
                .createdAt(LocalDateTime.now())
                .build();
        
        when(hotelRepository.save(any(Hotel.class))).thenReturn(savedHotel);
        when(favoriteRepository.countFavoritesByHotelId(any(UUID.class))).thenReturn(0L);
        
        // When
        HotelResponse response = hotelService.createHotel(request);
        
        // Then
        assertNotNull(response);
        assertEquals(request.getName(), response.getName());
        assertEquals(request.getCity(), response.getCity());
        assertEquals(request.getStarRating(), response.getStarRating());
        assertEquals(0L, response.getFavoriteCount());
        
        verify(hotelRepository).save(any(Hotel.class));
        verify(favoriteRepository).countFavoritesByHotelId(any(UUID.class));
    }
    
    @Test
    void testUpdateHotel_Success() {
        // Given
        stubRoomAvailability();
        stubSearchCacheInvalidation();
        HotelRequest request = HotelRequest.builder()
                .name("Updated Hotel")
                .description("Updated description")
                .city("Updated City")
                .country("Updated Country")
                .starRating(5)
                .amenities(Arrays.asList("WiFi", "Gym", "Spa"))
                .build();
        
        when(hotelRepository.findById(testHotelId)).thenReturn(Optional.of(testHotel));
        when(hotelRepository.save(testHotel)).thenReturn(testHotel);
        when(favoriteRepository.countFavoritesByHotelId(testHotelId)).thenReturn(3L);
        
        // When
        HotelResponse response = hotelService.updateHotel(testHotelId, request);
        
        // Then
        assertNotNull(response);
        assertEquals("Updated Hotel", testHotel.getName());
        assertEquals("Updated City", testHotel.getCity());
        assertEquals(5, testHotel.getStarRating());
        
        verify(hotelRepository).findById(testHotelId);
        verify(hotelRepository).save(testHotel);
        verify(favoriteRepository).countFavoritesByHotelId(testHotelId);
    }
    
    @Test
    void testUpdateHotel_NotFound() {
        // Given
        HotelRequest request = HotelRequest.builder()
                .name("Updated Hotel")
                .build();
        
        when(hotelRepository.findById(testHotelId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(HotelNotFoundException.class, () -> {
            hotelService.updateHotel(testHotelId, request);
        });
        
        verify(hotelRepository).findById(testHotelId);
        verify(hotelRepository, never()).save(any(Hotel.class));
    }
    
    @Test
    void testDeleteHotel_Success() {
        // Given
        stubSearchCacheInvalidation();
        when(hotelRepository.findById(testHotelId)).thenReturn(Optional.of(testHotel));
        
        // When
        hotelService.deleteHotel(testHotelId);
        
        // Then
        verify(hotelRepository).findById(testHotelId);
        verify(hotelRepository).delete(testHotel);
    }
    
    @Test
    void testGetAllCities() {
        // Given
        List<String> cities = Arrays.asList("City1", "City2", "City3");
        when(hotelRepository.findAllCities()).thenReturn(cities);
        
        // When
        List<String> result = hotelService.getAllCities();
        
        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(cities, result);
        verify(hotelRepository).findAllCities();
    }
    
    @Test
    void testGetAllCountries() {
        // Given
        List<String> countries = Arrays.asList("Country1", "Country2", "Country3");
        when(hotelRepository.findAllCountries()).thenReturn(countries);
        
        // When
        List<String> result = hotelService.getAllCountries();
        
        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(countries, result);
        verify(hotelRepository).findAllCountries();
    }

    private void stubRoomAvailability() {
        lenient().when(roomService.getRoomAvailabilities(anyList())).thenReturn(Map.of());
    }

    private void stubSearchCache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void stubSearchCacheInvalidation() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
    }

    private RoomType roomType(UUID id, Hotel hotel, String name) {
        return RoomType.builder()
                .id(id)
                .hotel(hotel)
                .name(name)
                .description(name + " room")
                .capacity(2)
                .pricePerNight(BigDecimal.valueOf(100))
                .totalInventory(10)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
