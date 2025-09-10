package com.hotel.hotel.service;

import com.hotel.hotel.dto.HotelRequest;
import com.hotel.hotel.dto.HotelResponse;
import com.hotel.hotel.dto.RoomTypeResponse;
import com.hotel.hotel.dto.SearchCriteria;
import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.RoomType;
import com.hotel.hotel.exception.HotelNotFoundException;
import com.hotel.hotel.exception.RoomTypeNotFoundException;
import com.hotel.hotel.repository.HotelRepository;
import com.hotel.hotel.repository.RoomTypeRepository;
import com.hotel.hotel.repository.UserFavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class HotelService {
    
    private final HotelRepository hotelRepository;
    private final UserFavoriteRepository favoriteRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RoomService roomService;
    private final RoomTypeRepository roomTypeRepository;
    
    private static final String HOTEL_CACHE_KEY = "hotel:";
    private static final String SEARCH_CACHE_KEY = "search:";
    private static final int CACHE_TTL_MINUTES = 10;
    
    @Cacheable(value = "hotels", key = "#hotelId")
    public HotelResponse getHotelById(UUID hotelId) {
        log.info("Getting hotel by id: {}", hotelId);
        
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found with id: " + hotelId));
        
        return mapToResponse(hotel, null);
    }
    
    public HotelResponse getHotelById(UUID hotelId, UUID userId) {
        log.info("Getting hotel by id: {} for user: {}", hotelId, userId);
        
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found with id: " + hotelId));
        
        return mapToResponse(hotel, userId);
    }
    
    public Page<HotelResponse> searchHotels(SearchCriteria criteria, Pageable pageable) {
        return searchHotels(criteria, pageable, null);
    }

    @Transactional(readOnly = true)
    public HotelResponse getHotelByRoomTypeId(UUID roomTypeId) {
        log.debug("Finding hotel by room type id: {}", roomTypeId);

        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new RoomTypeNotFoundException("Room type not found with id: " + roomTypeId));

        // roomType.getHotel() null check (Lazy Loading)
        Hotel hotel = roomType.getHotel();
        if (hotel == null) {
            throw new HotelNotFoundException("Hotel not found for room type id: " + roomTypeId);
        }

        // use mapToResponse with null userId
        return mapToResponse(hotel, null);
    }
    
    public Page<HotelResponse> searchHotels(SearchCriteria criteria, Pageable pageable, UUID userId) {
        log.info("Searching hotels with criteria: {}", criteria);
        
        String cacheKey = SEARCH_CACHE_KEY + criteria.hashCode() + ":" + pageable.hashCode();
        
        // Check cache with safe type checking
        if (userId == null) { // Only use cache for anonymous users
            try {
                Object cachedObject = redisTemplate.opsForValue().get(cacheKey);
                if (cachedObject instanceof Page<?>) {
                    @SuppressWarnings("unchecked")
                    Page<HotelResponse> cached = (Page<HotelResponse>) cachedObject;
                    log.debug("Found cached search results");
                    return cached;
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve from cache, proceeding with database query: {}", e.getMessage());
                // Remove corrupted cache entry
                redisTemplate.delete(cacheKey);
            }
        }
        
        // Build dynamic query specification
        Specification<Hotel> spec = buildSearchSpecification(criteria);
        
        Page<Hotel> hotels = hotelRepository.findAll(spec, pageable);
        Page<HotelResponse> response = hotels.map(hotel -> mapToResponse(hotel, userId));
        
        // Cache results for anonymous users only (10 minutes) with error handling
        if (userId == null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                log.debug("Cached search results for key: {}", cacheKey);
            } catch (Exception e) {
                log.warn("Failed to cache search results: {}", e.getMessage());
                // Continue execution even if caching fails
            }
        }
        
        return response;
    }
    
    public HotelResponse createHotel(HotelRequest request) {
        log.info("Creating new hotel: {}", request.getName());
        
        Hotel hotel = Hotel.builder()
                .name(request.getName())
                .description(request.getDescription())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .starRating(request.getStarRating())
                .amenities(request.getAmenities())
                .build();
        
        Hotel saved = hotelRepository.save(hotel);
        
        // Clear search cache
        clearSearchCache();
        
        return mapToResponse(saved, null);
    }
    
    @CacheEvict(value = "hotels", key = "#hotelId")
    public HotelResponse updateHotel(UUID hotelId, HotelRequest request) {
        log.info("Updating hotel: {}", hotelId);
        
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found"));
        
        hotel.setName(request.getName());
        hotel.setDescription(request.getDescription());
        hotel.setAddress(request.getAddress());
        hotel.setCity(request.getCity());
        hotel.setCountry(request.getCountry());
        hotel.setLatitude(request.getLatitude());
        hotel.setLongitude(request.getLongitude());
        hotel.setStarRating(request.getStarRating());
        hotel.setAmenities(request.getAmenities());
        
        Hotel updated = hotelRepository.save(hotel);
        
        clearSearchCache();
        
        return mapToResponse(updated, null);
    }
    
    @CacheEvict(value = "hotels", key = "#hotelId")
    public void deleteHotel(UUID hotelId) {
        log.info("Deleting hotel: {}", hotelId);
        
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found"));
        
        hotelRepository.delete(hotel);
        clearSearchCache();
    }
    
    public List<String> getAllCities() {
        return hotelRepository.findAllCities();
    }
    
    public List<String> getAllCountries() {
        return hotelRepository.findAllCountries();
    }
    
    private Specification<Hotel> buildSearchSpecification(SearchCriteria criteria) {
        Specification<Hotel> spec = Specification.where(null);
        
        if (criteria.getCity() != null && !criteria.getCity().trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.lower(root.get("city")), criteria.getCity().toLowerCase()));
        }
        
        if (criteria.getCountry() != null && !criteria.getCountry().trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.lower(root.get("country")), criteria.getCountry().toLowerCase()));
        }
        
        if (criteria.getMinRating() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("starRating"), criteria.getMinRating()));
        }
        
        if (criteria.getMaxRating() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("starRating"), criteria.getMaxRating()));
        }
        
        if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> {
                String pattern = "%" + criteria.getKeyword().toLowerCase() + "%";
                return cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                );
            });
        }
        
        return spec;
    }
    
    private HotelResponse mapToResponse(Hotel hotel, UUID userId) {
        // get all room types for this hotel
        List<UUID> roomTypeIds = hotel.getRoomTypes() != null ?
                hotel.getRoomTypes().stream().map(RoomType::getId).collect(Collectors.toList()) :
                List.of();

        // get availability for each room type
        Map<UUID, Integer> availabilityMap = roomService.getRoomAvailabilities(roomTypeIds);

        List<RoomTypeResponse> roomTypes = hotel.getRoomTypes() != null ?
                hotel.getRoomTypes().stream()
                        .map(roomType -> roomService.mapToResponse(roomType, availabilityMap.getOrDefault(roomType.getId(), 0)))
                        .collect(Collectors.toList()) : Collections.emptyList();
        
        // Calculate price range
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        if (hotel.getRoomTypes() != null && !hotel.getRoomTypes().isEmpty()) {
            minPrice = hotel.getRoomTypes().stream()
                    .map(RoomType::getPricePerNight)
                    .min(BigDecimal::compareTo)
                    .orElse(null);
            maxPrice = hotel.getRoomTypes().stream()
                    .map(RoomType::getPricePerNight)
                    .max(BigDecimal::compareTo)
                    .orElse(null);
        }
        
        // Check if user has saved this hotel as favorite
        Boolean isFavorite = null;
        if (userId != null) {
            isFavorite = favoriteRepository.existsByUserIdAndHotelId(userId, hotel.getId());
        }
        
        // Get favorite count
        Long favoriteCount = favoriteRepository.countFavoritesByHotelId(hotel.getId());
        
        return HotelResponse.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .description(hotel.getDescription())
                .address(hotel.getAddress())
                .city(hotel.getCity())
                .country(hotel.getCountry())
                .latitude(hotel.getLatitude())
                .longitude(hotel.getLongitude())
                .starRating(hotel.getStarRating())
                .amenities(hotel.getAmenities())
                .roomTypes(roomTypes)
                .createdAt(hotel.getCreatedAt())
                .favoriteCount(favoriteCount)
                .isFavorite(isFavorite)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .build();
    }
    
    private RoomTypeResponse mapRoomTypeToResponse(RoomType roomType) {
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
                .availableRooms(roomType.getTotalInventory()) // This should be calculated from inventory service
                .isAvailable(roomType.getTotalInventory() > 0)
                .build();
    }
    
    private void clearSearchCache() {
        try {
            Set<String> keys = redisTemplate.keys(SEARCH_CACHE_KEY + "*");
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Cleared {} search cache entries", keys.size());
            }
        } catch (Exception e) {
            log.error("Error clearing search cache", e);
        }
    }
}