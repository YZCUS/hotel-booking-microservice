package com.hotel.hotel.exception;

public class FavoriteNotFoundException extends RuntimeException {
    public FavoriteNotFoundException(String message) {
        super(message);
    }
    
    public FavoriteNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}