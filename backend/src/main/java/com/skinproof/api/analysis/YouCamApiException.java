package com.skinproof.api.analysis;

import org.springframework.http.HttpStatus;

public class YouCamApiException extends RuntimeException {
    private final HttpStatus httpStatus;

    public YouCamApiException(String message) {
        this(message, HttpStatus.BAD_GATEWAY, null);
    }

    public YouCamApiException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_GATEWAY, cause);
    }

    public YouCamApiException(String message, HttpStatus httpStatus) {
        this(message, httpStatus, null);
    }

    public YouCamApiException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}