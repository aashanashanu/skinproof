package com.skinproof.api.report;

public record ReportVerdictSnapshot(
        String label,
        String confidence,
        Double baselineMean,
        Double currentMean,
        Double delta,
        double noiseFloor,
        int validCaptureCount,
        int elapsedWeeks,
        String explanation) {
}