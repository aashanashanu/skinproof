package com.skinproof.api.analysis;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@RestControllerAdvice
public class YouCamExceptionHandler {
    @ExceptionHandler(YouCamApiException.class)
    public ResponseEntity<Map<String, String>> handleYouCamFailure(YouCamApiException exception) {
        return ResponseEntity.status(BAD_GATEWAY)
                .body(Map.of("error", "skin_analysis_failed", "message", exception.getMessage()));
    }
}