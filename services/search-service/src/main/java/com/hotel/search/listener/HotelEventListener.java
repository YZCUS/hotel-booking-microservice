package com.hotel.search.listener;

import com.hotel.search.config.RabbitMQConfig;
import com.hotel.search.model.HotelDocument;
import com.hotel.search.service.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class HotelEventListener {
    
    private final IndexService indexService;
    
    @RabbitListener(queues = RabbitMQConfig.HOTEL_CREATED_QUEUE)
    public void handleHotelCreated(HotelCreatedEvent event) {
        log.info("Received hotel created event: {}", event.getHotelId());
        
        try {
            HotelDocument document = mapToHotelDocument(event);
            indexService.indexHotel(document);
            log.info("Successfully indexed new hotel: {}", event.getHotelId());
        } catch (Exception e) {
            log.error("Failed to index new hotel: {}", event.getHotelId(), e);
        }
    }
    
    @RabbitListener(queues = RabbitMQConfig.HOTEL_UPDATED_QUEUE)
    public void handleHotelUpdated(HotelUpdatedEvent event) {
        log.info("Received hotel updated event: {}", event.getHotelId());
        
        try {
            HotelDocument document = mapToHotelDocument(event);
            indexService.updateHotel(document);
            log.info("Successfully updated hotel index: {}", event.getHotelId());
        } catch (Exception e) {
            log.error("Failed to update hotel index: {}", event.getHotelId(), e);
        }
    }
    
    @RabbitListener(queues = RabbitMQConfig.HOTEL_DELETED_QUEUE)
    public void handleHotelDeleted(HotelDeletedEvent event) {
        log.info("Received hotel deleted event: {}", event.getHotelId());
        
        try {
            indexService.deleteHotel(event.getHotelId().toString());
            log.info("Successfully removed hotel from index: {}", event.getHotelId());
        } catch (Exception e) {
            log.error("Failed to remove hotel from index: {}", event.getHotelId(), e);
        }
    }
    
    private HotelDocument mapToHotelDocument(HotelCreatedEvent event) {
        return HotelDocument.builder()
                .id(event.getHotelId())
                .name(event.getName())
                .description(event.getDescription())
                .city(event.getCity())
                .country(event.getCountry())
                .address(event.getAddress())
                .starRating(event.getStarRating())
                .amenities(event.getAmenities())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .imageUrls(event.getImageUrls())
                .isActive(true)
                .build();
    }
    
    private HotelDocument mapToHotelDocument(HotelUpdatedEvent event) {
        return HotelDocument.builder()
                .id(event.getHotelId())
                .name(event.getName())
                .description(event.getDescription())
                .city(event.getCity())
                .country(event.getCountry())
                .address(event.getAddress())
                .starRating(event.getStarRating())
                .amenities(event.getAmenities())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .imageUrls(event.getImageUrls())
                .isActive(event.getIsActive())
                .build();
    }
    
    // Event classes - these would typically be in a shared library
    public static class HotelCreatedEvent {
        private UUID hotelId;
        private String name;
        private String description;
        private String city;
        private String country;
        private String address;
        private Integer starRating;
        private List<String> amenities;
        private Double latitude;
        private Double longitude;
        private List<String> imageUrls;
        
        // Getters and setters
        public UUID getHotelId() { return hotelId; }
        public void setHotelId(UUID hotelId) { this.hotelId = hotelId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public Integer getStarRating() { return starRating; }
        public void setStarRating(Integer starRating) { this.starRating = starRating; }
        public List<String> getAmenities() { return amenities; }
        public void setAmenities(List<String> amenities) { this.amenities = amenities; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public List<String> getImageUrls() { return imageUrls; }
        public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    }
    
    public static class HotelUpdatedEvent extends HotelCreatedEvent {
        private Boolean isActive;
        
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }
    
    public static class HotelDeletedEvent {
        private UUID hotelId;
        
        public UUID getHotelId() { return hotelId; }
        public void setHotelId(UUID hotelId) { this.hotelId = hotelId; }
    }
}