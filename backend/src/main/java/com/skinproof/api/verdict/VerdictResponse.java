package com.skinproof.api.verdict;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VerdictResponse(
        UUID verdictId,
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
        int elapsedWeeks,
        boolean residualNoise,
        String explanation,
        List<ConfoundFlag> confounds,
        Instant computedAt) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static VerdictResponse from(VerdictEntity entity) {
        return new VerdictResponse(
                entity.getId(),
                entity.getConcern(),
                VerdictLabel.valueOf(entity.getLabel()),
                ConfidenceLevel.valueOf(entity.getConfidence()),
                entity.getBaselineMean(),
                entity.getCurrentMean(),
                entity.getDelta(),
                entity.getNoiseFloor(),
                entity.getBaselineCaptureCount(),
                entity.getValidCaptureCount(),
                entity.getFollowUpCaptureCount(),
                entity.getElapsedWeeks(),
                entity.isResidualNoise(),
                entity.getExplanation(),
                parseConfounds(entity.getConfoundFlagsJson()),
                entity.getComputedAt());
    }

    private static List<ConfoundFlag> parseConfounds(String json) {
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, ConfoundFlag.class));
        } catch (Exception exception) {
            return List.of();
        }
    }
}