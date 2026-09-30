package com.skinproof.api.capture;

public record CaptureQualityRequest(
        boolean hasFace,
        String position,
        String frontal,
        String lighting) {
    public boolean passes() {
        return hasFace
                && "good".equalsIgnoreCase(position)
                && "good".equalsIgnoreCase(frontal)
                && ("good".equalsIgnoreCase(lighting) || "ok".equalsIgnoreCase(lighting));
    }
}