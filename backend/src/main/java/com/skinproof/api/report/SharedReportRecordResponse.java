package com.skinproof.api.report;

import java.time.Instant;
import java.util.UUID;

public record SharedReportRecordResponse(
        UUID reportId,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        boolean includesImages) {
}