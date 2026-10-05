package com.skinproof.api.report;

import java.time.Instant;
import java.util.UUID;

public record CreateSharedReportResponse(UUID reportId, String token, Instant expiresAt, boolean includesImages) {
}