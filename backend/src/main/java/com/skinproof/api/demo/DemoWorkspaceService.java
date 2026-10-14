package com.skinproof.api.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.analysis.SkinAnalysisScore;
import com.skinproof.api.analysis.SkinConcern;
import com.skinproof.api.capture.AnalysisEntity;
import com.skinproof.api.capture.AnalysisRepository;
import com.skinproof.api.capture.CaptureEntity;
import com.skinproof.api.capture.CaptureMaskService;
import com.skinproof.api.capture.CaptureRepository;
import com.skinproof.api.capture.PrivateImageStore;
import com.skinproof.api.experiment.CreateExperimentRequest;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.experiment.ExperimentRepository;
import com.skinproof.api.experiment.ExperimentResponse;
import com.skinproof.api.verdict.CaptureMeasurement;
import com.skinproof.api.verdict.ConfoundCode;
import com.skinproof.api.verdict.ConfoundFlag;
import com.skinproof.api.verdict.NoiseCalibrationEntity;
import com.skinproof.api.verdict.NoiseCalibrationRepository;
import com.skinproof.api.verdict.VerdictAssessment;
import com.skinproof.api.verdict.VerdictEngine;
import com.skinproof.api.verdict.VerdictEntity;
import com.skinproof.api.verdict.VerdictRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Seeds and clears clearly-labeled synthetic demo data; never used for real captures.
 */
@Service
public class DemoWorkspaceService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ExperimentRepository experiments;
    private final CaptureRepository captures;
    private final AnalysisRepository analyses;
    private final VerdictRepository verdicts;
    private final NoiseCalibrationRepository calibrations;
    private final PrivateImageStore imageStore;
    private final VerdictEngine verdictEngine;

    public DemoWorkspaceService(
            ExperimentRepository experiments,
            CaptureRepository captures,
            AnalysisRepository analyses,
            VerdictRepository verdicts,
            NoiseCalibrationRepository calibrations,
            PrivateImageStore imageStore,
            VerdictEngine verdictEngine) {
        this.experiments = experiments;
        this.captures = captures;
        this.analyses = analyses;
        this.verdicts = verdicts;
        this.calibrations = calibrations;
        this.imageStore = imageStore;
        this.verdictEngine = verdictEngine;
    }

    @Transactional
    public List<ExperimentResponse> seed(UUID workspaceId) {
        if (!experiments.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This workspace already has experiments; seed only runs on an empty workspace.");
        }

        List<ExperimentEntity> created = new ArrayList<>();
        created.add(seedImproved(workspaceId));
        created.add(seedConfounded(workspaceId));
        created.add(seedInProgress(workspaceId));
        return created.stream().map(ExperimentResponse::from).toList();
    }

    @Transactional
    public void reset(UUID workspaceId) {
        reset(OwnerScope.demo(workspaceId));
    }

    @Transactional
    public void reset(OwnerScope owner) {
        List<ExperimentEntity> owned = experiments.findAllByOwnerTypeAndOwnerId(owner.type(), owner.id());
        for (ExperimentEntity experiment : owned) deleteExperimentData(experiment);
        experiments.deleteAll(owned);
    }

    @Transactional
    public void deleteExperiment(UUID experimentId, OwnerScope owner) {
        ExperimentEntity experiment = experiments.findByIdAndOwnerTypeAndOwnerId(
                        experimentId, owner.type(), owner.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        deleteExperimentData(experiment);
        experiments.delete(experiment);
    }

    private void deleteExperimentData(ExperimentEntity experiment) {
        UUID experimentId = experiment.getId();
        verdicts.deleteAllByExperiment_Id(experimentId);
        calibrations.deleteAllByExperiment_Id(experimentId);

        List<CaptureEntity> experimentCaptures = captures.findAllByExperiment_IdOrderByTakenAtAsc(experimentId);
        java.util.Map<UUID, AnalysisEntity> captureAnalyses = new java.util.HashMap<>();
        for (CaptureEntity capture : experimentCaptures) {
            captureAnalyses.put(capture.getAnalysis().getId(), capture.getAnalysis());
            if (!capture.isSimulated()) imageStore.delete(capture.getImageKey());
        }
        captures.deleteAll(experimentCaptures);
        captures.flush();
        for (var entry : captureAnalyses.entrySet()) {
            if (captures.findAllByAnalysis_Id(entry.getKey()).isEmpty()) {
                CaptureMaskService.storageKeys(entry.getValue()).forEach(imageStore::delete);
                analyses.deleteById(entry.getKey());
            }
        }
    }

    private ExperimentEntity seedImproved(UUID workspaceId) {
        LocalDate startDate = LocalDate.now().minusWeeks(8);
        ExperimentEntity experiment = experiments.save(new ExperimentEntity(workspaceId, new CreateExperimentRequest(
                "Barrier repair cream (simulated demo)", "TEXTURE", startDate, 8,
                "Simulated demo data illustrating a successful eight-week trial. Not a real outcome.")));

        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        List<CaptureSeed> schedule = new ArrayList<>();
        schedule.add(new CaptureSeed(start, true, 48.0));
        schedule.add(new CaptureSeed(start.plus(2, ChronoUnit.DAYS), true, 51.0));
        double[] followUpScores = {54.0, 58.0, 61.0, 65.0, 69.0, 72.0, 75.0, 78.0};
        for (int week = 1; week <= followUpScores.length; week++) {
            schedule.add(new CaptureSeed(start.plus(week * 7L, ChronoUnit.DAYS), false, followUpScores[week - 1]));
        }
        seedCapturesAndVerdict(experiment, SkinConcern.TEXTURE, schedule, List.of());
        return experiment;
    }

    private ExperimentEntity seedConfounded(UUID workspaceId) {
        LocalDate startDate = LocalDate.now().minusWeeks(6);
        ExperimentEntity experiment = experiments.save(new ExperimentEntity(workspaceId, new CreateExperimentRequest(
                "Retinol serum (simulated demo)", "ACNE", startDate, 8,
                "Simulated demo data illustrating a confounded, inconclusive trial. Not a real outcome.")));

        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        List<CaptureSeed> schedule = new ArrayList<>();
        schedule.add(new CaptureSeed(start, true, 60.0));
        schedule.add(new CaptureSeed(start.plus(2, ChronoUnit.DAYS), true, 57.0));
        double[] followUpScores = {63.0, 55.0, 68.0, 59.0, 70.0, 61.0};
        for (int week = 1; week <= followUpScores.length; week++) {
            schedule.add(new CaptureSeed(start.plus(week * 7L, ChronoUnit.DAYS), false, followUpScores[week - 1]));
        }
        List<ConfoundFlag> confounds = List.of(
                new ConfoundFlag(ConfoundCode.NEW_PRODUCT),
                new ConfoundFlag(ConfoundCode.ILLNESS));
        seedCapturesAndVerdict(experiment, SkinConcern.ACNE, schedule, confounds);
        return experiment;
    }

    private ExperimentEntity seedInProgress(UUID workspaceId) {
        LocalDate startDate = LocalDate.now().minusWeeks(2);
        ExperimentEntity experiment = experiments.save(new ExperimentEntity(workspaceId, new CreateExperimentRequest(
                "Vitamin C serum (simulated demo)", "RADIANCE", startDate, 8,
                "Simulated demo data illustrating an in-progress trial with limited captures so far.")));

        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        List<CaptureSeed> schedule = List.of(
                new CaptureSeed(start, true, 55.0),
                new CaptureSeed(start.plus(7, ChronoUnit.DAYS), false, 57.0));
        seedCapturesAndVerdict(experiment, SkinConcern.RADIANCE, schedule, List.of());
        return experiment;
    }

    private void seedCapturesAndVerdict(
            ExperimentEntity experiment,
            SkinConcern concern,
            List<CaptureSeed> schedule,
            List<ConfoundFlag> confounds) {
        List<CaptureMeasurement> measurements = new ArrayList<>();
        for (CaptureSeed seed : schedule) {
            String cacheKey = "demo-" + UUID.randomUUID();
            AnalysisEntity analysis = new AnalysisEntity(cacheKey, concern.action(), 0);
            analysis.succeed("SIMULATED-" + UUID.randomUUID(), toScoresJson(concern, seed.rawScore()));
            analyses.save(analysis);

            String imageKey = "simulated/" + UUID.randomUUID();
            String sampleKey = placeholderHash();
            captures.save(CaptureEntity.simulated(experiment, analysis, imageKey, sampleKey, seed.baseline(), seed.takenAt()));

            measurements.add(new CaptureMeasurement(sampleKey, seed.takenAt(), seed.baseline(), true, false, seed.rawScore()));
        }

        VerdictAssessment assessment = verdictEngine.assess(concern.action(), measurements, confounds);
        NoiseCalibrationEntity calibration = new NoiseCalibrationEntity(experiment, concern.action());
        calibration.update(assessment);
        calibrations.save(calibration);
        verdicts.save(new VerdictEntity(experiment, assessment, toConfoundsJson(confounds)));
    }

    private static String placeholderHash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }

    private static String toScoresJson(SkinConcern concern, double rawScore) {
        int uiScore = (int) Math.round(Math.min(100, rawScore + 10));
        SkinAnalysisScore score = new SkinAnalysisScore(concern.action(), rawScore, uiScore, List.of());
        try {
            return JSON.writeValueAsString(List.of(score));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize simulated demo scores.", exception);
        }
    }

    private static String toConfoundsJson(List<ConfoundFlag> confounds) {
        try {
            return JSON.writeValueAsString(confounds);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize simulated demo confounds.", exception);
        }
    }
}
