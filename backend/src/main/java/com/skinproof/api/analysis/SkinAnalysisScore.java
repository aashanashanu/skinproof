package com.skinproof.api.analysis;

import java.util.List;

public record SkinAnalysisScore(
        String concern,
        Double rawScore,
        Integer uiScore,
        List<String> maskUrls) {
}