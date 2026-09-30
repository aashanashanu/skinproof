package com.skinproof.api.capture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinproof.api.analysis.SkinAnalysisResponse;
import com.skinproof.api.analysis.YouCamApiException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AnalysisJobWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisJobWorker.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CaptureAnalysisService captures;
    private final AnalysisRepository analyses;
    private final PrivateImageStore images;
    private final com.skinproof.api.analysis.YouCamSkinAnalysisService youCam;

    public AnalysisJobWorker(
            CaptureAnalysisService captures,
            AnalysisRepository analyses,
            PrivateImageStore images,
            com.skinproof.api.analysis.YouCamSkinAnalysisService youCam) {
        this.captures = captures;
        this.analyses = analyses;
        this.images = images;
        this.youCam = youCam;
    }

    @Scheduled(fixedDelayString = "${skinproof.analysis.poller-interval:PT2S}")
    public void processOneReadyJob() {
        AnalysisEntity job = captures.claimNext();
        if (job == null) return;

        try {
            CaptureEntity capture = captures.firstCaptureForAnalysis(job.getId());
            byte[] image = images.get(capture.getImageKey());
            List<com.skinproof.api.analysis.SkinConcern> concerns = captures.concernsFor(job);
            String extension = "image/png".equals(capture.getContentType()) ? ".png" : ".jpg";
            String fileName = capture.getImageKey().substring(capture.getImageKey().lastIndexOf('/') + 1) + extension;
            SkinAnalysisResponse result = youCam.analyze(
                    image,
                    fileName,
                    capture.getContentType(),
                    concerns);
            job.succeed(result.taskId(), toJson(result.scores()));
            captures.saveAnalysis(job);
        } catch (YouCamApiException exception) {
            recordFailure(job, exception.getHttpStatus() == HttpStatus.UNPROCESSABLE_ENTITY
                    ? "PROVIDER_REJECTED"
                    : "PROVIDER_ERROR", exception.getMessage(), exception.getHttpStatus() == HttpStatus.UNPROCESSABLE_ENTITY);
        } catch (ResponseStatusException exception) {
            recordFailure(job, "PROVIDER_UNAVAILABLE", exception.getReason(), false);
        } catch (RuntimeException exception) {
            LOGGER.warn("Analysis job {} failed on attempt {}", job.getId(), job.getAttemptCount(), exception);
            recordFailure(job, "ANALYSIS_FAILED", "Analysis service failed; retry will be attempted when available.", false);
        }
    }

    private void recordFailure(AnalysisEntity job, String code, String message, boolean permanent) {
        if (permanent || job.getAttemptCount() >= captures.maxAttempts()) {
            job.fail(code, safeMessage(message));
        } else {
            long retrySeconds = Math.min(60, captures.retryBase().toSeconds() * (1L << (job.getAttemptCount() - 1)));
            job.retryAt(Instant.now().plusSeconds(retrySeconds), code, safeMessage(message));
        }
        captures.saveAnalysis(job);
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) return "Analysis failed.";
        String sanitized = message.replaceAll("[\\r\\n\\t]", " ");
        return sanitized.length() > 480 ? sanitized.substring(0, 480) : sanitized;
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize analysis scores.", exception);
        }
    }
}