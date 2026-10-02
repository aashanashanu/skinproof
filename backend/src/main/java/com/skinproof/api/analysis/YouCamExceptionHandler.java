package com.skinproof.api.analysis;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class YouCamExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(YouCamExceptionHandler.class);

    @ExceptionHandler(YouCamApiException.class)
    public ResponseEntity<Map<String, String>> handleYouCamFailure(YouCamApiException exception) {
        LOGGER.warn("YouCam skin analysis request failed with HTTP {}: {}",
                exception.getHttpStatus().value(), exception.getMessage());
        return ResponseEntity.status(exception.getHttpStatus())
                .body(Map.of("error", "skin_analysis_failed", "message", exception.getMessage()));
    }
}