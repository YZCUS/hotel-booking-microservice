package com.hotel.notification.template;

import com.hotel.notification.dto.BookingConfirmationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class EmailTemplates {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' HH:mm");
    
    public String getBookingConfirmationTemplate(BookingConfirmationData data) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Booking Confirmation</title>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #2c3e50; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; border: 1px solid #ddd; }
                    .booking-details { background-color: #f8f9fa; padding: 15px; margin: 15px 0; border-radius: 5px; }
                    .footer { background-color: #34495e; color: white; padding: 15px; text-align: center; font-size: 12px; }
                    .button { background-color: #3498db; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block; margin: 10px 0; }
                    .important { font-weight: bold; color: #e74c3c; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Booking Confirmation</h1>
                        <p>Your reservation has been confirmed!</p>
                    </div>
                    
                    <div class="content">
                        <h2>Dear %s,</h2>
                        <p>Thank you for choosing our hotel booking service. Your reservation has been successfully confirmed.</p>
                        
                        <div class="booking-details">
                            <h3>Booking Details</h3>
                            <p><strong>Confirmation Number:</strong> %s</p>
                            <p><strong>Hotel:</strong> %s</p>
                            <p><strong>Address:</strong> %s</p>
                            <p><strong>Check-in:</strong> %s</p>
                            <p><strong>Check-out:</strong> %s</p>
                            <p><strong>Guests:</strong> %d</p>
                            <p><strong>Total Amount:</strong> $%.2f</p>
                            <p><strong>Booking Date:</strong> %s</p>
                        </div>
                        
                        <div class="booking-details">
                            <h3>Hotel Contact Information</h3>
                            <p><strong>Phone:</strong> %s</p>
                            <p><strong>Email:</strong> %s</p>
                        </div>
                        
                        <div class="important">
                            <h3>Important Information</h3>
                            <p>Please bring a valid ID and credit card for check-in.</p>
                            <p>Cancellation Policy: %s</p>
                        </div>
                        
                        <p>If you have any questions or need to make changes to your reservation, please contact us immediately.</p>
                        
                        <a href="#" class="button">View Booking Details</a>
                    </div>
                    
                    <div class="footer">
                        <p>© 2024 Hotel Booking Service. All rights reserved.</p>
                        <p>This is an automated message. Please do not reply to this email.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                data.getUserName(),
                data.getConfirmationNumber() != null ? data.getConfirmationNumber() : data.getBookingId().toString().substring(0, 8).toUpperCase(),
                data.getHotelName(),
                data.getHotelAddress() != null ? data.getHotelAddress() : "N/A",
                data.getCheckInDate().format(DATE_FORMATTER),
                data.getCheckOutDate().format(DATE_FORMATTER),
                data.getGuests(),
                data.getTotalPrice(),
                data.getBookingTime() != null ? data.getBookingTime().format(DATETIME_FORMATTER) : "N/A",
                data.getHotelPhone() != null ? data.getHotelPhone() : "Contact hotel directly",
                data.getHotelEmail() != null ? data.getHotelEmail() : "Contact hotel directly",
                data.getCancellationPolicy() != null ? data.getCancellationPolicy() : "Please contact hotel for cancellation policy"
            );
    }
    
    public String getBookingCancellationTemplate(BookingConfirmationData data) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Booking Cancellation</title>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #e74c3c; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; border: 1px solid #ddd; }
                    .booking-details { background-color: #f8f9fa; padding: 15px; margin: 15px 0; border-radius: 5px; }
                    .footer { background-color: #34495e; color: white; padding: 15px; text-align: center; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Booking Cancellation</h1>
                        <p>Your reservation has been cancelled</p>
                    </div>
                    
                    <div class="content">
                        <h2>Dear %s,</h2>
                        <p>Your booking has been successfully cancelled as requested.</p>
                        
                        <div class="booking-details">
                            <h3>Cancelled Booking Details</h3>
                            <p><strong>Confirmation Number:</strong> %s</p>
                            <p><strong>Hotel:</strong> %s</p>
                            <p><strong>Original Check-in:</strong> %s</p>
                            <p><strong>Original Check-out:</strong> %s</p>
                            <p><strong>Total Amount:</strong> $%.2f</p>
                        </div>
                        
                        <p>If you need to book again or have any questions, please don't hesitate to contact us.</p>
                    </div>
                    
                    <div class="footer">
                        <p>© 2024 Hotel Booking Service. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                data.getUserName(),
                data.getConfirmationNumber() != null ? data.getConfirmationNumber() : data.getBookingId().toString().substring(0, 8).toUpperCase(),
                data.getHotelName(),
                data.getCheckInDate().format(DATE_FORMATTER),
                data.getCheckOutDate().format(DATE_FORMATTER),
                data.getTotalPrice()
            );
    }
    
    public String getWelcomeEmailTemplate(String userName, String userEmail) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Welcome to Hotel Booking Service</title>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #27ae60; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; border: 1px solid #ddd; }
                    .footer { background-color: #34495e; color: white; padding: 15px; text-align: center; font-size: 12px; }
                    .button { background-color: #3498db; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block; margin: 10px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Welcome to Hotel Booking Service!</h1>
                    </div>
                    
                    <div class="content">
                        <h2>Dear %s,</h2>
                        <p>Welcome to our hotel booking platform! We're excited to have you as a new member.</p>
                        
                        <p>With your account, you can:</p>
                        <ul>
                            <li>Search and book hotels worldwide</li>
                            <li>Manage your bookings</li>
                            <li>Save your favorite hotels</li>
                            <li>Get exclusive deals and offers</li>
                        </ul>
                        
                        <p>Start exploring amazing hotels and destinations today!</p>
                        
                        <a href="#" class="button">Start Booking</a>
                    </div>
                    
                    <div class="footer">
                        <p>© 2024 Hotel Booking Service. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName);
    }
}