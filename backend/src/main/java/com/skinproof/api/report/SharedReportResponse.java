package com.skinproof.api.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SharedReportResponse(
        SharedReportSnapshot report,
        Instant expiresAt,
        Map<String, String> imageUrls) {
    public SharedReportResponse {
        imageUrls = imageUrls == null ? Map.of() : Map.copyOf(imageUrls);
    }
}