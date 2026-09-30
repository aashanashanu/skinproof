package com.skinproof.api.verdict;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinproof.api.analysis.SkinAnalysisScore;
import com.skinproof.api.analysis.SkinConcern;
import com.skinproof.api.capture.CaptureEntity;
import com.skinproof.api.capture.CaptureRepository;
import com.skinproof.api.capture.AnalysisStatus;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.experiment.ExperimentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VerdictService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ExperimentRepository experiments;
    private final CaptureRepository captures;
    private final NoiseCalibrationRepository calibrations;
    private final VerdictRepository verdicts;
    private final VerdictEngine engine;

    public VerdictService(
            ExperimentRepository experiments,
            CaptureRepository captures,
            NoiseCalibrationRepository calibrations,
            VerdictRepository verdicts,
            VerdictEngine engine) {
        this.experiments = experiments;
        this.captures = captures;
        this.calibrations = calibrations;
        this.verdicts = verdicts;
        this.engine = engine;
    }

    @Transactional
    public VerdictResponse getLatest(UUID experimentId, UUID workspaceId) {
        ExperimentEntity experiment = findExperiment(experimentId, workspaceId);
        String concern = targetAction(experiment);
        return verdicts.findFirstByExperiment_IdAndConcernOrderByComputedAtDesc(experimentId, concern)
                .map(VerdictResponse::from)
                .orElseGet(() -> recompute(experiment, List.of()));
    }

    @Transactional
    public VerdictResponse recompute(UUID experimentId, UUID workspaceId, List<ConfoundFlag> confounds) {
        return recompute(findExperiment(experimentId, workspaceId), confounds == null ? List.of() : confounds);
    }

    @Transactional
    public void recomputeForAnalysis(UUID analysisId) {
        captures.findAllByAnalysis_Id(analysisId).stream()
                .map(capture -> capture.getExperiment().getId())
                .distinct()
            .forEach(experimentId -> experiments.findById(experimentId).ifPresent(experiment -> {
                String concern = targetAction(experiment);
                List<ConfoundFlag> previousConfounds = verdicts
                    .findFirstByExperiment_IdAndConcernOrderByComputedAtDesc(experimentId, concern)
                    .map(entity -> parseConfounds(entity.getConfoundFlagsJson()))
                    .orElse(List.of());
                recompute(experiment, previousConfounds);
            }));
    }

    private VerdictResponse recompute(ExperimentEntity experiment, List<ConfoundFlag> confounds) {
        String concern = targetAction(experiment);
        List<CaptureMeasurement> measurements = captures.findAllByExperiment_IdOrderByTakenAtAsc(experiment.getId())
                .stream()
                .filter(capture -> capture.getAnalysis().getStatus() == AnalysisStatus.SUCCEEDED)
                .map(capture -> measurement(capture, concern))
                .filter(java.util.Objects::nonNull)
                .toList();
        VerdictAssessment assessment = engine.assess(concern, measurements, confounds);
        NoiseCalibrationEntity calibration = calibrations
                .findByExperiment_IdAndConcern(experiment.getId(), concern)
                .orElseGet(() -> new NoiseCalibrationEntity(experiment, concern));
        calibration.update(assessment);
        calibrations.save(calibration);

        VerdictEntity saved = verdicts.save(new VerdictEntity(experiment, assessment, toJson(confounds)));
        return VerdictResponse.from(saved);
    }

    private ExperimentEntity findExperiment(UUID experimentId, UUID workspaceId) {
        return experiments.findByIdAndWorkspaceId(experimentId, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static String targetAction(ExperimentEntity experiment) {
        try {
            return SkinConcern.valueOf(experiment.getTargetConcern()).action();
        } catch (IllegalArgumentException exception) {
            return experiment.getTargetConcern().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static CaptureMeasurement measurement(CaptureEntity capture, String concern) {
        try {
            List<SkinAnalysisScore> scores = JSON.readValue(
                    capture.getAnalysis().getConcernScoresJson(), new TypeReference<>() {});
            Double rawScore = scores.stream()
                    .filter(score -> concern.equals(score.concern()))
                    .map(SkinAnalysisScore::rawScore)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            return new CaptureMeasurement(
                    capture.getImageSha256(),
                    capture.getTakenAt(),
                    capture.isBaseline(),
                    capture.isValid(),
                    capture.isSimulated(),
                    rawScore);
        } catch (JsonProcessingException | RuntimeException exception) {
            return null;
        }
    }

    private static String toJson(List<ConfoundFlag> confounds) {
        try {
            return JSON.writeValueAsString(confounds);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize verdict confounds.", exception);
        }
    }

    private static List<ConfoundFlag> parseConfounds(String json) {
        try {
            return JSON.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException | RuntimeException exception) {
            return List.of();
        }
    }
}