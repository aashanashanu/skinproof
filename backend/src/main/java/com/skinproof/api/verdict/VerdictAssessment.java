package com.skinproof.api.verdict;

import java.util.List;

public record VerdictAssessment(
        String concern,
        VerdictLabel label,
        ConfidenceLevel confidence,
        Double baselineMean,
        Double currentMean,
        Double delta,
        double noiseFloor,
        int baselineCaptureCount,
        int validCaptureCount,
        int followUpCaptureCount,
        long elapsedWeeks,
        boolean residualNoise,
        String explanation,
        List<ConfoundFlag> confounds) {
    public VerdictAssessment {
        confounds = List.copyOf(confounds);
    }
}