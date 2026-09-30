package com.skinproof.api.analysis;

public class YouCamApiException extends RuntimeException {
    public YouCamApiException(String message) {
        super(message);
    }

    public YouCamApiException(String message, Throwable cause) {
        super(message, cause);
    }
}