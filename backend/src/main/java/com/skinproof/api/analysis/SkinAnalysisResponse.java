package com.skinproof.api.analysis;

import java.util.List;

public record SkinAnalysisResponse(
        String taskId,
        String mode,
        int documentedUnitEstimate,
        List<SkinAnalysisScore> scores) {
}