package com.skinproof.api.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinproof.api.analysis.SkinAnalysisScore;
import com.skinproof.api.analysis.SkinConcern;
import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.OwnerType;
import com.skinproof.api.capture.AnalysisStatus;
import com.skinproof.api.capture.CaptureEntity;
import com.skinproof.api.capture.CaptureRepository;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.experiment.ExperimentRepository;
import com.skinproof.api.verdict.VerdictEntity;
import com.skinproof.api.verdict.VerdictRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class SharedReportService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration REPORT_LIFETIME = Duration.ofDays(30);
    private static final Duration IMAGE_LINK_LIFETIME = Duration.ofMinutes(15);
    private static final String DISCLAIMER = "Shared SkinProof summaries show cosmetic trends, not medical advice or proof that a product caused a change.";
    private static final String VERDICT_DISCLOSURE = "This summary describes the observed cosmetic trend only; it cannot establish that a product caused the change.";

    private final SharedReportRepository reports;
    private final ExperimentRepository experiments;
    private final CaptureRepository captures;
    private final VerdictRepository verdicts;
    private final ObjectMapper json;
    private final S3Presigner presigner;
    private final String imageBucket;

    public SharedReportService(
            SharedReportRepository reports,
            ExperimentRepository experiments,
            CaptureRepository captures,
            VerdictRepository verdicts,
            ObjectMapper json,
            S3Presigner presigner,
            @Value("${skinproof.storage.image-bucket:}") String imageBucket) {
        this.reports = reports;
        this.experiments = experiments;
        this.captures = captures;
        this.verdicts = verdicts;
        this.json = json;
        this.presigner = presigner;
        this.imageBucket = imageBucket;
    }

    @Transactional
    public CreateSharedReportResponse create(UUID experimentId, OwnerScope owner, CreateSharedReportRequest request) {
        if (owner.type() != OwnerType.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sign in to create a shareable report.");
        }
        ExperimentEntity experiment = experiments.findByIdAndOwnerTypeAndOwnerId(experimentId, owner.type(), owner.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Instant now = Instant.now();
        UUID reportId = UUID.randomUUID();
        String token = generateToken();
        List<CaptureEntity> successfulCaptures = captures.findAllByExperiment_IdOrderByTakenAtAsc(experimentId).stream()
                .filter(capture -> capture.getAnalysis().getStatus() == AnalysisStatus.SUCCEEDED)
                .toList();
        String targetAction = targetAction(experiment.getTargetConcern());
        List<ReportCaptureSnapshot> captureSnapshots = new ArrayList<>();
        Map<String, String> privateImageKeys = new HashMap<>();
        int captureNumber = 0;
        for (CaptureEntity capture : successfulCaptures) {
            captureNumber++;
            Double score = rawScore(capture, targetAction);
            captureSnapshots.add(new ReportCaptureSnapshot(
                    captureNumber, capture.getTakenAt(), capture.isBaseline(), capture.isSimulated(), score));
            if (request.includeImages() && !capture.isSimulated()) {
                privateImageKeys.put(Integer.toString(captureNumber), capture.getImageKey());
            }
        }

        VerdictEntity latestVerdict = verdicts.findFirstByExperiment_IdAndConcernOrderByComputedAtDesc(experimentId, targetAction)
                .orElse(null);
        ReportVerdictSnapshot verdictSnapshot = latestVerdict == null ? null : toSnapshot(latestVerdict);
        SharedReportSnapshot snapshot = new SharedReportSnapshot(
                experiment.getProductName(),
                experiment.getTargetConcern(),
                experiment.getStartDate(),
                experiment.getDurationWeeks(),
                now,
                successfulCaptures.stream().anyMatch(CaptureEntity::isSimulated),
                DISCLAIMER,
                verdictSnapshot,
                List.copyOf(captureSnapshots));

        SharedReportEntity entity = new SharedReportEntity(
                reportId, experiment, owner, hashToken(token), toJson(snapshot), toJson(privateImageKeys),
                request.includeImages(), now, now.plus(REPORT_LIFETIME));
        reports.save(entity);
        return new CreateSharedReportResponse(reportId, token, entity.getExpiresAt(), request.includeImages());
    }

    @Transactional
    public void revoke(UUID reportId, OwnerScope owner) {
        SharedReportEntity report = reports.findByIdAndOwnerTypeAndOwnerId(reportId, owner.type(), owner.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        report.revoke(Instant.now());
        reports.save(report);
    }

    @Transactional(readOnly = true)
    public List<SharedReportRecordResponse> list(UUID experimentId, OwnerScope owner) {
        experiments.findByIdAndOwnerTypeAndOwnerId(experimentId, owner.type(), owner.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return reports.findAllByExperiment_IdAndOwnerTypeAndOwnerIdOrderByCreatedAtDesc(
                        experimentId, owner.type(), owner.id()).stream()
                .map(report -> new SharedReportRecordResponse(
                        report.getId(), report.getCreatedAt(), report.getExpiresAt(), report.getRevokedAt(),
                        report.isIncludeImages()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SharedReportResponse readPublic(String rawToken) {
        if (rawToken == null || rawToken.length() < 40 || rawToken.length() > 100) throw unavailable();
        SharedReportEntity report = reports.findByTokenHash(hashToken(rawToken)).orElseThrow(SharedReportService::unavailable);
        Instant now = Instant.now();
        if (report.getRevokedAt() != null || !report.getExpiresAt().isAfter(now)) throw unavailable();
        SharedReportSnapshot snapshot = fromJson(report.getSnapshotJson());
        Map<String, String> imageUrls = report.isIncludeImages()
                ? presignImages(readImageKeys(report.getImageKeysJson()))
                : Map.of();
        return new SharedReportResponse(snapshot, report.getExpiresAt(), imageUrls);
    }

    private Map<String, String> presignImages(Map<String, String> imageKeys) {
        if (imageKeys.isEmpty()) return Map.of();
        if (imageBucket.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image sharing is not configured.");
        }
        Map<String, String> urls = new HashMap<>();
        imageKeys.forEach((captureNumber, key) -> {
            GetObjectRequest get = GetObjectRequest.builder().bucket(imageBucket).key(key).build();
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(IMAGE_LINK_LIFETIME)
                    .getObjectRequest(get)
                    .build();
            urls.put(captureNumber, presigner.presignGetObject(request).url().toString());
        });
        return Map.copyOf(urls);
    }

    private ReportVerdictSnapshot toSnapshot(VerdictEntity verdict) {
        return new ReportVerdictSnapshot(
                verdict.getLabel(), verdict.getConfidence(), verdict.getBaselineMean(), verdict.getCurrentMean(),
                verdict.getDelta(), verdict.getNoiseFloor(), verdict.getValidCaptureCount(), verdict.getElapsedWeeks(),
            VERDICT_DISCLOSURE);
    }

    private Double rawScore(CaptureEntity capture, String concern) {
        try {
            return json.readValue(capture.getAnalysis().getConcernScoresJson(), new TypeReference<List<SkinAnalysisScore>>() {})
                    .stream()
                    .filter(score -> concern.equals(score.concern()))
                    .map(SkinAnalysisScore::rawScore)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String targetAction(String storedConcern) {
        try {
            return SkinConcern.valueOf(storedConcern).action();
        } catch (IllegalArgumentException ignored) {
            return storedConcern.toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize the shared report snapshot.", exception);
        }
    }

    private SharedReportSnapshot fromJson(String snapshotJson) {
        try {
            return json.readValue(snapshotJson, SharedReportSnapshot.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read the shared report snapshot.", exception);
        }
    }

    private Map<String, String> readImageKeys(String json) {
        try {
            return this.json.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Report link is unavailable.");
    }
}
