package com.skinproof.api.capture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.OwnerType;
import com.skinproof.api.analysis.SkinAnalysisResponse;
import com.skinproof.api.analysis.SkinConcern;
import com.skinproof.api.analysis.YouCamApiException;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.experiment.ExperimentRepository;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaptureAnalysisService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<AnalysisStatus> DAILY_CAP_STATUSES = List.of(
            AnalysisStatus.QUEUED, AnalysisStatus.PROCESSING, AnalysisStatus.RETRY_WAIT, AnalysisStatus.SUCCEEDED);

    private final ExperimentRepository experiments;
    private final CaptureRepository captures;
    private final AnalysisRepository analyses;
    private final PrivateImageStore imageStore;
    private final CaptureQualityService qualityService;
    private final int dailyCap;

    public CaptureAnalysisService(
            ExperimentRepository experiments,
            CaptureRepository captures,
            AnalysisRepository analyses,
            PrivateImageStore imageStore,
            CaptureQualityService qualityService,
            @Value("${skinproof.analysis.daily-cap-per-experiment:3}") int dailyCap) {
        this.experiments = experiments;
        this.captures = captures;
        this.analyses = analyses;
        this.imageStore = imageStore;
        this.qualityService = qualityService;
        this.dailyCap = dailyCap;
    }

    @Transactional
    public CaptureResponse createCapture(
            UUID experimentId,
            UUID workspaceId,
            byte[] imageBytes,
            String contentType,
            CaptureQualityRequest cameraQuality,
            boolean baseline,
            List<SkinConcern> concerns) {
        return createCapture(experimentId, OwnerScope.demo(workspaceId), imageBytes, contentType, cameraQuality, baseline, concerns);
    }

    @Transactional
    public CaptureResponse createCapture(
            UUID experimentId,
            OwnerScope owner,
            byte[] imageBytes,
            String contentType,
            CaptureQualityRequest cameraQuality,
            boolean baseline,
            List<SkinConcern> concerns) {
        ExperimentEntity experiment = findExperiment(experimentId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        CaptureQualityAssessment quality = qualityService.assess(imageBytes, cameraQuality);
        String imageHash = sha256(imageBytes);
        List<SkinConcern> canonicalConcerns = concerns.stream().distinct().sorted().toList();
        String actions = canonicalConcerns.stream().map(SkinConcern::action).reduce((a, b) -> a + "," + b).orElseThrow();
        String cacheKey = sha256((imageHash + "\n" + actions).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String imageKey = experimentId + "/captures/" + UUID.randomUUID();

        AnalysisEntity analysis = analyses.findByCacheKey(cacheKey).orElse(null);
        if (analysis == null) {
            long analysesToday = analyses.countDailyAttempts(experimentId, Instant.now().minus(1, ChronoUnit.DAYS), DAILY_CAP_STATUSES);
            if (analysesToday >= dailyCap) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "This experiment reached its daily skin-analysis limit. Try again tomorrow.");
            }
            imageStore.put(imageKey, imageBytes, contentType);
            int units = documentedUnits(canonicalConcerns.size());
            try {
                analysis = analyses.saveAndFlush(new AnalysisEntity(cacheKey, actions, units));
            } catch (DataIntegrityViolationException concurrentDuplicate) {
                analysis = analyses.findByCacheKey(cacheKey).orElseThrow(() -> concurrentDuplicate);
            }
        } else {
            imageStore.put(imageKey, imageBytes, contentType);
        }

        CaptureEntity capture = captures.save(new CaptureEntity(
                experiment,
                analysis,
                imageKey,
                imageHash,
                contentType,
                quality.score(),
                toJson(quality.flags()),
                baseline,
                quality.valid()));
        return CaptureResponse.from(capture);
    }

    @Transactional(readOnly = true)
    public CaptureResponse getCapture(UUID captureId, UUID workspaceId) {
        return getCapture(captureId, OwnerScope.demo(workspaceId));
    }

    @Transactional(readOnly = true)
    public CaptureResponse getCapture(UUID captureId, OwnerScope owner) {
        return findCapture(captureId, owner)
                .map(CaptureResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<CaptureResponse> listCaptures(UUID experimentId, UUID workspaceId) {
        return listCaptures(experimentId, OwnerScope.demo(workspaceId));
    }

    @Transactional(readOnly = true)
    public List<CaptureResponse> listCaptures(UUID experimentId, OwnerScope owner) {
        findExperiment(experimentId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return captures.findAllByExperiment_IdOrderByTakenAtAsc(experimentId).stream()
                .map(CaptureResponse::from)
                .toList();
    }

    @Transactional
    public CaptureResponse retryAnalysis(UUID captureId, UUID workspaceId) {
        return retryAnalysis(captureId, OwnerScope.demo(workspaceId));
    }

    @Transactional
    public CaptureResponse retryAnalysis(UUID captureId, OwnerScope owner) {
        CaptureEntity capture = findCapture(captureId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AnalysisEntity analysis = capture.getAnalysis();
        if (analysis.getStatus() != AnalysisStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed analysis jobs can be retried.");
        }
        analysis.queueRetry();
        analyses.save(analysis);
        return CaptureResponse.from(capture);
    }

    private java.util.Optional<ExperimentEntity> findExperiment(UUID experimentId, OwnerScope owner) {
        if (owner.type() == OwnerType.DEMO) return experiments.findByIdAndWorkspaceId(experimentId, owner.id());
        return experiments.findByIdAndOwnerTypeAndOwnerId(experimentId, owner.type(), owner.id());
    }

    private java.util.Optional<CaptureEntity> findCapture(UUID captureId, OwnerScope owner) {
        if (owner.type() == OwnerType.DEMO) return captures.findByIdAndWorkspaceId(captureId, owner.id());
        return captures.findByIdAndOwnerTypeAndOwnerId(captureId, owner.type(), owner.id());
    }

    byte[] readImage(String key) {
        return imageStore.get(key);
    }

    @Transactional
    AnalysisEntity claimNext() {
        List<AnalysisEntity> jobs = analyses.findRunnable(
                List.of(AnalysisStatus.QUEUED, AnalysisStatus.RETRY_WAIT), Instant.now());
        if (jobs.isEmpty()) return null;
        AnalysisEntity job = jobs.getFirst();
        job.startAttempt();
        return analyses.saveAndFlush(job);
    }

    @Transactional(readOnly = true)
    CaptureEntity firstCaptureForAnalysis(UUID analysisId) {
        return captures.findFirstByAnalysisIdOrderByTakenAtAsc(analysisId).orElseThrow();
    }

    @Transactional(readOnly = true)
    List<SkinConcern> concernsFor(AnalysisEntity analysis) {
        return java.util.Arrays.stream(analysis.getConcernActions().split(","))
                .map(action -> java.util.Arrays.stream(SkinConcern.values())
                        .filter(concern -> concern.action().equals(action))
                        .findFirst()
                        .orElseThrow())
                .toList();
    }

    @Transactional
    void saveAnalysis(AnalysisEntity analysis) {
        analyses.save(analysis);
    }

    int maxAttempts() {
        return 3;
    }

    java.time.Duration retryBase() {
        return java.time.Duration.ofSeconds(10);
    }

    private static String toJson(List<String> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize capture quality flags.", exception);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static int documentedUnits(int count) {
        if (count <= 4) return 9;
        if (count <= 8) return 12;
        if (count <= 12) return 14;
        return 16;
    }
}