package com.skinproof.api.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SkinAnalysisResultParser {
    public List<SkinAnalysisScore> parse(JsonNode taskResponse) {
        JsonNode output = taskResponse.path("data").path("results").path("output");
        if (!output.isArray()) {
            throw new YouCamApiException("YouCam response did not contain an analysis result list.");
        }

        List<SkinAnalysisScore> scores = new ArrayList<>();
        for (JsonNode result : output) {
            String concern = result.path("type").asText("");
            if (concern.isBlank()) {
                continue;
            }

            List<String> maskUrls = new ArrayList<>();
            JsonNode masks = result.path("mask_urls");
            if (masks.isArray()) {
                masks.forEach(mask -> {
                    if (mask.isTextual()) {
                        maskUrls.add(mask.asText());
                    }
                });
            }

            scores.add(new SkinAnalysisScore(
                    concern,
                    result.path("raw_score").isNumber() ? result.path("raw_score").asDouble() : null,
                    result.path("ui_score").isIntegralNumber() ? result.path("ui_score").asInt() : null,
                    List.copyOf(maskUrls)));
        }

        if (scores.isEmpty()) {
            throw new YouCamApiException("YouCam response contained no supported concern scores.");
        }
        return List.copyOf(scores);
    }
}