package com.skinproof.api.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinproof.api.analysis.SkinAnalysisScore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaptureResponse(
        UUID captureId,
        int qualityScore,
        List<String> qualityFlags,
        boolean baseline,
        boolean valid,
        boolean simulated,
        Instant takenAt,
        AnalysisStatus analysisStatus,
        String providerTaskId,
        List<SkinAnalysisScore> scores,
        String errorCode,
        String errorMessage,
        int attemptCount) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static CaptureResponse from(CaptureEntity capture) {
        AnalysisEntity analysis = capture.getAnalysis();
        List<String> flags = parseFlags(capture.getQualityFlagsJson());
        List<SkinAnalysisScore> scores = parseScores(analysis.getConcernScoresJson()).stream()
            .map(score -> new SkinAnalysisScore(score.concern(), score.rawScore(), score.uiScore(),
                java.util.stream.IntStream.range(0, score.maskUrls().size())
                    .mapToObj(index -> "/api/captures/" + capture.getId() + "/masks/"
                        + org.springframework.web.util.UriUtils.encodePathSegment(
                            score.concern(), java.nio.charset.StandardCharsets.UTF_8)
                        + "/" + index)
                    .toList()))
            .toList();
        return new CaptureResponse(
                capture.getId(),
                capture.getQualityScore(),
                flags,
                capture.isBaseline(),
                capture.isValid(),
                capture.isSimulated(),
                capture.getTakenAt(),
                analysis.getStatus(),
                analysis.getProviderTaskId(),
                scores,
                analysis.getErrorCode(),
                analysis.getErrorMessage(),
                analysis.getAttemptCount());
    }

    private static List<String> parseFlags(String json) {
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static List<SkinAnalysisScore> parseScores(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode scores = MAPPER.readTree(json);
            return MAPPER.convertValue(scores, MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, SkinAnalysisScore.class));
        } catch (Exception exception) {
            return List.of();
        }
    }
}