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
import java.util.*;
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
    private static final String SEARCH_CACHE_VERSION_KEY = "search:version";
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
        
        String cacheKey = SEARCH_CACHE_KEY + getSearchCacheVersion() + ":" + criteria.hashCode() + ":" + pageable.hashCode();
        
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
        Page<HotelResponse> response = mapSearchResults(hotels, userId);
        
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

    private Page<HotelResponse> mapSearchResults(Page<Hotel> hotels, UUID userId) {
        List<Hotel> hotelList = hotels.getContent();
        if (hotelList.isEmpty()) {
            return hotels.map(hotel -> mapToResponse(hotel, userId));
        }

        List<UUID> hotelIds = hotelList.stream()
                .map(Hotel::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<RoomType> pageRoomTypes = roomTypeRepository.findByHotelIdIn(hotelIds);
        if (pageRoomTypes == null) {
            pageRoomTypes = List.of();
        }

        Map<UUID, List<RoomType>> roomTypesByHotelId = pageRoomTypes.stream()
                .filter(roomType -> roomType.getHotel() != null && roomType.getHotel().getId() != null)
                .collect(Collectors.groupingBy(roomType -> roomType.getHotel().getId()));

        List<UUID> roomTypeIds = pageRoomTypes.stream()
                .map(RoomType::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<UUID, Integer> availabilityMap = roomTypeIds.isEmpty()
                ? Map.of()
                : roomService.getRoomAvailabilities(roomTypeIds);

        Map<UUID, Long> favoriteCounts = getFavoriteCounts(hotelIds);
        Set<UUID> favoriteHotelIds = getFavoriteHotelIds(userId, hotelIds);

        return hotels.map(hotel -> {
            UUID hotelId = hotel.getId();
            List<RoomType> roomTypes = roomTypesByHotelId.getOrDefault(hotelId, List.of());
            Boolean isFavorite = userId == null ? null : favoriteHotelIds.contains(hotelId);
            Long favoriteCount = favoriteCounts.getOrDefault(hotelId, 0L);
            return mapToResponse(hotel, roomTypes, availabilityMap, favoriteCount, isFavorite);
        });
    }

    private Map<UUID, Long> getFavoriteCounts(List<UUID> hotelIds) {
        if (hotelIds.isEmpty()) {
            return Map.of();
        }

        List<Object[]> rows = favoriteRepository.countFavoritesByHotelIds(hotelIds);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            if (row.length >= 2 && row[0] instanceof UUID hotelId && row[1] instanceof Number count) {
                counts.put(hotelId, count.longValue());
            }
        }
        return counts;
    }

    private Set<UUID> getFavoriteHotelIds(UUID userId, List<UUID> hotelIds) {
        if (userId == null || hotelIds.isEmpty()) {
            return Set.of();
        }

        List<UUID> favoriteHotelIds = favoriteRepository.findFavoriteHotelIdsByUserIdAndHotelIdIn(userId, hotelIds);
        if (favoriteHotelIds == null || favoriteHotelIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(favoriteHotelIds);
    }

    private HotelResponse mapToResponse(Hotel hotel, UUID userId) {
        List<RoomType> hotelRoomTypes = hotel.getRoomTypes() == null ? List.of() : hotel.getRoomTypes();
        List<UUID> roomTypeIds = hotelRoomTypes.stream()
                .map(RoomType::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<UUID, Integer> availabilityMap = roomService.getRoomAvailabilities(roomTypeIds);
        
        Boolean isFavorite = null;
        if (userId != null) {
            isFavorite = favoriteRepository.existsByUserIdAndHotelId(userId, hotel.getId());
        }
        
        Long favoriteCount = favoriteRepository.countFavoritesByHotelId(hotel.getId());
        
        return mapToResponse(hotel, hotelRoomTypes, availabilityMap, favoriteCount, isFavorite);
    }

    private HotelResponse mapToResponse(
            Hotel hotel,
            List<RoomType> hotelRoomTypes,
            Map<UUID, Integer> availabilityMap,
            Long favoriteCount,
            Boolean isFavorite) {
        List<RoomType> safeRoomTypes = hotelRoomTypes == null ? List.of() : hotelRoomTypes;

        List<RoomTypeResponse> roomTypes = safeRoomTypes.stream()
                .map(roomType -> roomService.mapToResponse(roomType, availabilityMap.getOrDefault(roomType.getId(), 0)))
                .collect(Collectors.toList());

        BigDecimal minPrice = safeRoomTypes.stream()
                .map(RoomType::getPricePerNight)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
        BigDecimal maxPrice = safeRoomTypes.stream()
                .map(RoomType::getPricePerNight)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);

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
            Long version = redisTemplate.opsForValue().increment(SEARCH_CACHE_VERSION_KEY);
            log.info("Invalidated search cache namespace; version is now {}", version);
        } catch (Exception e) {
            log.error("Error clearing search cache", e);
        }
    }

    private long getSearchCacheVersion() {
        try {
            Object version = redisTemplate.opsForValue().get(SEARCH_CACHE_VERSION_KEY);
            if (version instanceof Number number) {
                return number.longValue();
            }
            if (version instanceof String value) {
                return Long.parseLong(value);
            }
        } catch (Exception e) {
            log.warn("Failed to read search cache version, using default namespace: {}", e.getMessage());
        }
        return 0L;
    }
}
