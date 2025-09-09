package com.hotel.notification.service;

import com.hotel.notification.dto.BookingConfirmationData;
import com.hotel.notification.dto.HotelInfo;
import com.hotel.notification.dto.UserInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.hotel.notification.exception.ServiceCommunicationException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final EmailService emailService;
    private final WebClient.Builder webClientBuilder;
    
    public void sendBookingConfirmation(BookingCreatedEvent event) {
        try {
            // Get user and hotel information
            UserInfo user = getUserInfo(event.getUserId());
            HotelInfo hotel = getHotelInfo(event.getRoomTypeId());
            
            BookingConfirmationData data = BookingConfirmationData.builder()
                .bookingId(event.getBookingId())
                .userName(user.getFullName())
                .userEmail(user.getEmail())
                .hotelName(hotel.getName())
                .hotelAddress(hotel.getAddress())
                .checkInDate(event.getCheckInDate())
                .checkOutDate(event.getCheckOutDate())
                .guests(event.getGuests())
                .totalPrice(event.getTotalPrice())
                .bookingTime(event.getCreatedAt())
                .hotelPhone(hotel.getPhoneNumber())
                .hotelEmail(hotel.getEmail())
                .cancellationPolicy("Free cancellation up to 24 hours before check-in")
                .build();
            
            emailService.sendBookingConfirmationEmail(data);
            log.info("Booking confirmation notification sent for booking: {}", event.getBookingId());
            
        } catch (Exception e) {
            log.error("Failed to send booking confirmation notification for booking: {}", event.getBookingId(), e);
            throw new RuntimeException("Failed to send booking confirmation", e);
        }
    }
    
    public void sendCancellationConfirmation(BookingCancelledEvent event) {
        try {
            // Get user and hotel information
            UserInfo user = getUserInfo(event.getUserId());
            HotelInfo hotel = getHotelInfo(event.getRoomTypeId());
            
            BookingConfirmationData data = BookingConfirmationData.builder()
                .bookingId(event.getBookingId())
                .userName(user.getFullName())
                .userEmail(user.getEmail())
                .hotelName(hotel.getName())
                .hotelAddress(hotel.getAddress())
                .checkInDate(event.getCheckInDate())
                .checkOutDate(event.getCheckOutDate())
                .totalPrice(event.getTotalPrice())
                .build();
            
            emailService.sendBookingCancellationEmail(data);
            log.info("Booking cancellation notification sent for booking: {}", event.getBookingId());
            
        } catch (Exception e) {
            log.error("Failed to send booking cancellation notification for booking: {}", event.getBookingId(), e);
            throw new RuntimeException("Failed to send cancellation confirmation", e);
        }
    }
    
    public void sendWelcomeMessage(UserRegisteredEvent event) {
        try {
            emailService.sendWelcomeEmail(event.getEmail(), event.getFullName());
            log.info("Welcome notification sent to user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to send welcome notification to user: {}", event.getUserId(), e);
        }
    }
    
    private UserInfo getUserInfo(UUID userId) {
        try {
            WebClient webClient = webClientBuilder.build();
            
            UserInfo userInfo = webClient.get()
                .uri("http://user-service:8081/api/v1/users/{userId}", userId)
                .header("X-Internal-Service", "notification-service")
                .retrieve()
                .bodyToMono(UserInfo.class)
                .block();
            
            if (userInfo == null || userInfo.getEmail() == null || userInfo.getEmail().trim().isEmpty()) {
                log.error("Invalid user data received for user: {}", userId);
                throw new ServiceCommunicationException("Cannot fetch valid user details for notification");
            }
            
            return userInfo;
                
        } catch (Exception e) {
            log.error("Failed to get user info for user: {}", userId, e);
            // Don't send email with default data - throw exception instead
            throw new ServiceCommunicationException("Cannot fetch user details for notification");
        }
    }
    
    private HotelInfo getHotelInfo(UUID roomTypeId) {
        try {
            WebClient webClient = webClientBuilder.build();
            
            // Get room type info which includes hotel details
            HotelInfo hotelInfo = webClient.get()
                .uri("http://hotel-service:8082/api/v1/hotels/rooms/{roomTypeId}/hotel-details", roomTypeId)
                .retrieve()
                .bodyToMono(HotelInfo.class)
                .block();
            
            if (hotelInfo == null || hotelInfo.getName() == null || hotelInfo.getName().trim().isEmpty()) {
                log.error("Invalid hotel data received for room type: {}", roomTypeId);
                throw new ServiceCommunicationException("Cannot fetch valid hotel details for notification");
            }
            
            return hotelInfo;
                
        } catch (Exception e) {
            log.error("Failed to get hotel info for room type: {}", roomTypeId, e);
            // Don't send email with default data - throw exception instead
            throw new ServiceCommunicationException("Cannot fetch hotel details for notification");
        }
    }
    
    // Event classes - these would typically be in a shared library
    @Getter
    @Setter
    public static class BookingCreatedEvent {
        // Getters and setters
        private UUID bookingId;
        private UUID userId;
        private UUID roomTypeId;
        private java.time.LocalDate checkInDate;
        private java.time.LocalDate checkOutDate;
        private Integer guests;
        private java.math.BigDecimal totalPrice;
        private LocalDateTime createdAt;
    }
    
    @Getter
    @Setter
    public static class BookingCancelledEvent {
        // Getters and setters
        private UUID bookingId;
        private UUID userId;
        private UUID roomTypeId;
        private java.time.LocalDate checkInDate;
        private java.time.LocalDate checkOutDate;
        private java.math.BigDecimal totalPrice;
        private LocalDateTime cancelledAt;
        private String reason;
    }

    @Getter
    @Setter
    public static class UserRegisteredEvent {
        private UUID userId;
        private String email;
        private String firstName;
        private String lastName;
        private String fullName;
        private LocalDateTime registeredAt;

        public String getFullName() {
            if (fullName != null) return fullName;
            if (firstName != null && lastName != null) return firstName + " " + lastName;
            return firstName != null ? firstName : "Guest";
        }
    }
}