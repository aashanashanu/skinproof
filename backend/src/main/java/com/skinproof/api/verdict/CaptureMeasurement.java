package com.skinproof.api.verdict;

import java.time.Instant;
import java.util.Objects;

public record CaptureMeasurement(
    String sampleKey,
        Instant takenAt,
        boolean baseline,
        boolean valid,
        boolean simulated,
        Double rawScore) {
    public CaptureMeasurement {
        if (sampleKey == null || sampleKey.isBlank()) throw new IllegalArgumentException("Sample key is required.");
        Objects.requireNonNull(takenAt, "Capture time is required.");
    }
}