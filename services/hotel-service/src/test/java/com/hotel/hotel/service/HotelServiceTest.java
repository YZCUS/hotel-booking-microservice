package com.hotel.hotel.service;

import com.hotel.hotel.dto.HotelRequest;
import com.hotel.hotel.dto.HotelResponse;
import com.hotel.hotel.dto.HotelExportResponse;
import com.hotel.hotel.dto.RoomTypeResponse;
import com.hotel.hotel.dto.SearchCriteria;
import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.RoomType;
import com.hotel.hotel.event.EventPublisher;
import com.hotel.hotel.exception.HotelNotFoundException;
import com.hotel.hotel.repository.HotelRepository;
import com.hotel.hotel.repository.RoomTypeRepository;
import com.hotel.hotel.repository.UserFavoriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    @Mock
    private EventPublisher eventPublisher;
    
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
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(roomService.getRoomAvailabilities(any())).thenReturn(java.util.Map.of());
    }
    
    @Test
    void testGetHotelById_Success() {
        // Given
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
        SearchCriteria criteria = SearchCriteria.builder()
                .city("Test City")
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        
        Page<Hotel> hotelPage = new PageImpl<>(Arrays.asList(testHotel));
        
        when(valueOperations.get(anyString())).thenReturn(null);
        when(hotelRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(hotelPage);
        when(favoriteRepository.countFavoritesByHotelId(testHotelId)).thenReturn(2L);
        
        // When
        Page<HotelResponse> result = hotelService.searchHotels(criteria, pageable);
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Test Hotel", result.getContent().get(0).getName());
        assertEquals("Test City", result.getContent().get(0).getCity());
        
        verify(hotelRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(valueOperations, never()).set(anyString(), any(), any(Long.class), any(TimeUnit.class));
    }

    @Test
    void testSearchHotels_BatchesAvailabilityAndFavoriteLookupsForPage() {
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

        when(hotelRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testHotel, secondHotel)));
        when(roomTypeRepository.findByHotelIdIn(any())).thenReturn(List.of(firstRoom, secondRoom));
        when(roomService.getRoomAvailabilities(any())).thenReturn(Map.of(
                firstRoomTypeId, 4,
                secondRoomTypeId, 7));
        when(roomService.mapToResponse(firstRoom, 4))
                .thenReturn(RoomTypeResponse.builder().id(firstRoomTypeId).availableRooms(4).build());
        when(roomService.mapToResponse(secondRoom, 7))
                .thenReturn(RoomTypeResponse.builder().id(secondRoomTypeId).availableRooms(7).build());
        when(favoriteRepository.countFavoritesByHotelIds(any()))
                .thenReturn(List.of(new Object[]{testHotelId, 2L}, new Object[]{secondHotelId, 1L}));

        Page<HotelResponse> result = hotelService.searchHotels(criteria, pageable);

        assertEquals(2, result.getContent().size());
        assertEquals(4, result.getContent().get(0).getRoomTypes().getFirst().getAvailableRooms());
        assertEquals(7, result.getContent().get(1).getRoomTypes().getFirst().getAvailableRooms());
        assertEquals(2L, result.getContent().get(0).getFavoriteCount());
        assertEquals(1L, result.getContent().get(1).getFavoriteCount());
        verify(roomService).getRoomAvailabilities(argThat(ids ->
                ids.size() == 2 && ids.contains(firstRoomTypeId) && ids.contains(secondRoomTypeId)));
        verify(favoriteRepository).countFavoritesByHotelIds(argThat(ids ->
                ids.size() == 2 && ids.contains(testHotelId) && ids.contains(secondHotelId)));
        verify(favoriteRepository, never()).countFavoritesByHotelId(any());
    }

    @Test
    void testSearchHotels_InventoryOutagePreservesUnknownAvailability() {
        SearchCriteria criteria = SearchCriteria.builder().city("Test City").build();
        UUID roomTypeId = UUID.randomUUID();
        RoomType roomType = roomType(roomTypeId, testHotel, "Deluxe");
        testHotel.setRoomTypes(List.of(roomType));
        RoomTypeResponse unknownAvailability = RoomTypeResponse.builder()
                .id(roomTypeId)
                .availableRooms(null)
                .isAvailable(null)
                .build();

        when(hotelRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testHotel)));
        when(roomTypeRepository.findByHotelIdIn(any())).thenReturn(List.of(roomType));
        when(roomService.getRoomAvailabilities(List.of(roomTypeId))).thenReturn(Map.of());
        when(roomService.mapToResponse(roomType, null)).thenReturn(unknownAvailability);

        Page<HotelResponse> result = hotelService.searchHotels(criteria, PageRequest.of(0, 20));

        assertNull(result.getContent().getFirst().getRoomTypes().getFirst().getAvailableRooms());
        verify(roomService).mapToResponse(roomType, null);
    }
    
    @Test
    void testSearchHotels_DoesNotUseRedisPageCache() {
        // Given
        SearchCriteria criteria = SearchCriteria.builder()
                .city("Test City")
                .build();
        Pageable pageable = PageRequest.of(0, 20);

        Page<Hotel> hotelPage = new PageImpl<>(Arrays.asList(testHotel));
        when(hotelRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(hotelPage);
        when(favoriteRepository.countFavoritesByHotelId(testHotelId)).thenReturn(2L);
        
        // When
        Page<HotelResponse> result = hotelService.searchHotels(criteria, pageable);
        
        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Test Hotel", result.getContent().get(0).getName());
        
        verify(valueOperations, never()).get(anyString());
        verify(hotelRepository).findAll(any(Specification.class), any(Pageable.class));
    }
    
    @Test
    void testCreateHotel_Success() {
        // Given
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
        verify(eventPublisher).publishHotelCreated(any());
    }
    
    @Test
    void testUpdateHotel_Success() {
        // Given
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
        verify(eventPublisher).publishHotelUpdated(any());
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
        when(hotelRepository.findById(testHotelId)).thenReturn(Optional.of(testHotel));
        
        // When
        hotelService.deleteHotel(testHotelId);
        
        // Then
        verify(hotelRepository).findById(testHotelId);
        verify(hotelRepository).delete(testHotel);
        verify(eventPublisher).publishHotelDeleted(any());
    }

    @Test
    void exportHotels_IncludesRoomProjectionAndAggregateAvailability() {
        UUID roomTypeId = UUID.randomUUID();
        RoomType roomType = RoomType.builder()
                .id(roomTypeId)
                .hotel(testHotel)
                .name("King")
                .capacity(2)
                .pricePerNight(new BigDecimal("125.00"))
                .totalInventory(5)
                .build();
        testHotel.setRoomTypes(List.of(roomType));
        RoomTypeResponse roomResponse = RoomTypeResponse.builder()
                .id(roomTypeId)
                .hotelId(testHotelId)
                .name("King")
                .capacity(2)
                .pricePerNight(new BigDecimal("125.00"))
                .totalInventory(5)
                .availableRooms(3)
                .isAvailable(true)
                .build();
        when(hotelRepository.findAll()).thenReturn(List.of(testHotel));
        when(roomService.getRoomAvailabilities(List.of(roomTypeId)))
                .thenReturn(java.util.Map.of(roomTypeId, 3));
        when(roomService.mapToResponse(roomType, 3)).thenReturn(roomResponse);
        when(favoriteRepository.countFavoritesByHotelId(testHotelId)).thenReturn(0L);

        List<HotelExportResponse> exported = hotelService.exportHotels();

        assertEquals(1, exported.size());
        assertEquals(5, exported.get(0).getTotalRooms());
        assertEquals(3, exported.get(0).getAvailableRooms());
        assertEquals(new BigDecimal("125.00"), exported.get(0).getMinPrice());
        assertEquals(roomResponse, exported.get(0).getRoomTypes().get(0));
        assertTrue(exported.get(0).getIsActive());
    }

    @Test
    void exportHotels_InventoryOutageKeepsCatalogAndMarksAvailabilityUnknown() {
        UUID roomTypeId = UUID.randomUUID();
        RoomType roomType = RoomType.builder()
                .id(roomTypeId)
                .hotel(testHotel)
                .name("King")
                .capacity(2)
                .pricePerNight(new BigDecimal("125.00"))
                .totalInventory(5)
                .build();
        testHotel.setRoomTypes(List.of(roomType));
        RoomTypeResponse roomResponse = RoomTypeResponse.builder()
                .id(roomTypeId)
                .totalInventory(5)
                .availableRooms(null)
                .isAvailable(null)
                .build();
        when(hotelRepository.findAll()).thenReturn(List.of(testHotel));
        when(roomService.getRoomAvailabilities(List.of(roomTypeId))).thenReturn(java.util.Map.of());
        when(roomService.mapToResponse(roomType, null)).thenReturn(roomResponse);

        List<HotelExportResponse> exported = hotelService.exportHotels();

        assertEquals(1, exported.size());
        assertEquals(5, exported.getFirst().getTotalRooms());
        assertNull(exported.getFirst().getAvailableRooms());
        assertEquals(roomResponse, exported.getFirst().getRoomTypes().getFirst());
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
