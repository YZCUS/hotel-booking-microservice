package com.hotel.hotel.exception;

public class RoomTypeNotFoundException extends RuntimeException {
    public RoomTypeNotFoundException(String message) {
        super(message);
    }
    
    public RoomTypeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}