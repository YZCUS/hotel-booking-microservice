package com.hotel.notification.service;

import com.hotel.notification.dto.BookingConfirmationData;
import com.hotel.notification.template.EmailTemplates;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    
    private final JavaMailSender mailSender;
    private final EmailTemplates templates;
    
    @Value("${spring.mail.from}")
    private String fromEmail;
    
    @Async
    public CompletableFuture<Void> sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // HTML content
            
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
    
    @Async
    public CompletableFuture<Void> sendBookingConfirmationEmail(BookingConfirmationData data) {
        try {
            String subject = "Booking Confirmation - " + data.getHotelName();
            String body = templates.getBookingConfirmationTemplate(data);
            
            return sendEmail(data.getUserEmail(), subject, body);
            
        } catch (Exception e) {
            log.error("Failed to send booking confirmation email for booking: {}", data.getBookingId(), e);
            throw new RuntimeException("Failed to send booking confirmation email", e);
        }
    }
    
    @Async
    public CompletableFuture<Void> sendBookingCancellationEmail(BookingConfirmationData data) {
        try {
            String subject = "Booking Cancellation - " + data.getHotelName();
            String body = templates.getBookingCancellationTemplate(data);
            
            return sendEmail(data.getUserEmail(), subject, body);
            
        } catch (Exception e) {
            log.error("Failed to send booking cancellation email for booking: {}", data.getBookingId(), e);
            throw new RuntimeException("Failed to send booking cancellation email", e);
        }
    }
    
    @Async
    public CompletableFuture<Void> sendWelcomeEmail(String userEmail, String userName) {
        try {
            String subject = "Welcome to Hotel Booking Service!";
            String body = templates.getWelcomeEmailTemplate(userName, userEmail);
            
            return sendEmail(userEmail, subject, body);
            
        } catch (Exception e) {
            log.error("Failed to send welcome email to: {}", userEmail, e);
            throw new RuntimeException("Failed to send welcome email", e);
        }
    }
    
    @Async
    public CompletableFuture<Void> sendPasswordResetEmail(String userEmail, String userName, String resetToken) {
        try {
            String subject = "Password Reset Request";
            String body = buildPasswordResetEmail(userName, resetToken);
            
            return sendEmail(userEmail, subject, body);
            
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", userEmail, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }
    
    private String buildPasswordResetEmail(String userName, String resetToken) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Password Reset</title>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #f39c12; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; border: 1px solid #ddd; }
                    .button { background-color: #e74c3c; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block; margin: 10px 0; }
                    .warning { background-color: #fff3cd; padding: 15px; border-radius: 5px; margin: 15px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Password Reset Request</h1>
                    </div>
                    
                    <div class="content">
                        <h2>Dear %s,</h2>
                        <p>We received a request to reset your password for your Hotel Booking Service account.</p>
                        
                        <p>Click the button below to reset your password:</p>
                        <a href="#" class="button">Reset Password</a>
                        
                        <div class="warning">
                            <p><strong>Security Notice:</strong></p>
                            <p>This link will expire in 1 hour for security reasons.</p>
                            <p>If you didn't request this password reset, please ignore this email.</p>
                        </div>
                        
                        <p>Reset Token: %s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName, resetToken);
    }
}