package com.skinproof.api.report;

import java.time.Instant;

public record ReportCaptureSnapshot(
        int captureNumber,
        Instant takenAt,
        boolean baseline,
        boolean simulated,
        Double rawScore) {
}