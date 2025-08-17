package com.hotel.hotel.exception;

public class DuplicateFavoriteException extends RuntimeException {
    public DuplicateFavoriteException(String message) {
        super(message);
    }
    
    public DuplicateFavoriteException(String message, Throwable cause) {
        super(message, cause);
    }
}