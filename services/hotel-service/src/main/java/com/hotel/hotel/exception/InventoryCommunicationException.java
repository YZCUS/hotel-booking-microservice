package com.hotel.hotel.exception;

public class InventoryCommunicationException extends RuntimeException {
    public InventoryCommunicationException(String message) {
        super(message);
    }

    public InventoryCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
