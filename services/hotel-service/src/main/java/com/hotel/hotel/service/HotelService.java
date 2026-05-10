package com.hotel.hotel.service;

import com.hotel.hotel.dto.HotelRequest;
import com.hotel.hotel.dto.HotelResponse;
import com.hotel.hotel.dto.RoomTypeResponse;
import com.hotel.hotel.dto.SearchCriteria;
import com.hotel.hotel.entity.Hotel;
import com.hotel.hotel.entity.RoomType;
import com.hotel.hotel.event.EventPublisher;
import com.hotel.hotel.event.HotelCreatedEvent;
import com.hotel.hotel.event.HotelDeletedEvent;
import com.hotel.hotel.event.HotelUpdatedEvent;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class HotelService {
    
    private final HotelRepository hotelRepository;
    private final UserFavoriteRepository favoriteRepository;
    private final RoomService roomService;
    private final RoomTypeRepository roomTypeRepository;
    private final EventPublisher eventPublisher;
    
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

        // Build dynamic query specification
        Specification<Hotel> spec = buildSearchSpecification(criteria);
        
        Page<Hotel> hotels = hotelRepository.findAll(spec, pageable);
        return hotels.map(hotel -> mapToResponse(hotel, userId));
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
        
        publishAfterCommit(() -> eventPublisher.publishHotelCreated(toHotelCreatedEvent(saved)));
        
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
        
        publishAfterCommit(() -> eventPublisher.publishHotelUpdated(toHotelUpdatedEvent(updated)));
        
        return mapToResponse(updated, null);
    }
    
    @CacheEvict(value = "hotels", key = "#hotelId")
    public void deleteHotel(UUID hotelId) {
        log.info("Deleting hotel: {}", hotelId);
        
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException("Hotel not found"));
        
        hotelRepository.delete(hotel);
        publishAfterCommit(() -> eventPublisher.publishHotelDeleted(
                HotelDeletedEvent.builder().hotelId(hotel.getId()).build()));
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
    
    private void publishAfterCommit(Runnable publisher) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publisher.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publisher.run();
                } catch (Exception e) {
                    log.error("Failed to publish hotel event after commit", e);
                }
            }
        });
    }

    private HotelCreatedEvent toHotelCreatedEvent(Hotel hotel) {
        return HotelCreatedEvent.builder()
                .hotelId(hotel.getId())
                .name(hotel.getName())
                .description(hotel.getDescription())
                .city(hotel.getCity())
                .country(hotel.getCountry())
                .address(hotel.getAddress())
                .starRating(hotel.getStarRating())
                .amenities(hotel.getAmenities())
                .latitude(toDouble(hotel.getLatitude()))
                .longitude(toDouble(hotel.getLongitude()))
                .imageUrls(List.of())
                .build();
    }

    private HotelUpdatedEvent toHotelUpdatedEvent(Hotel hotel) {
        return HotelUpdatedEvent.builder()
                .hotelId(hotel.getId())
                .name(hotel.getName())
                .description(hotel.getDescription())
                .city(hotel.getCity())
                .country(hotel.getCountry())
                .address(hotel.getAddress())
                .starRating(hotel.getStarRating())
                .amenities(hotel.getAmenities())
                .latitude(toDouble(hotel.getLatitude()))
                .longitude(toDouble(hotel.getLongitude()))
                .imageUrls(List.of())
                .isActive(true)
                .build();
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
