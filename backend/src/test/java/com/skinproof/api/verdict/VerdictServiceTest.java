package com.skinproof.api.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skinproof.api.capture.CaptureRepository;
import com.skinproof.api.experiment.CreateExperimentRequest;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.experiment.ExperimentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class VerdictServiceTest {
    @Test
    void firstVerdictReadCreatesCalibrationAndInconclusiveSnapshot() {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        CaptureRepository captures = org.mockito.Mockito.mock(CaptureRepository.class);
        NoiseCalibrationRepository calibrations = org.mockito.Mockito.mock(NoiseCalibrationRepository.class);
        VerdictRepository verdicts = org.mockito.Mockito.mock(VerdictRepository.class);
        UUID workspaceId = UUID.randomUUID();
        ExperimentEntity experiment = experiment(workspaceId);
        when(experiments.findByIdAndWorkspaceId(experiment.getId(), workspaceId)).thenReturn(Optional.of(experiment));
        when(verdicts.findFirstByExperiment_IdAndConcernOrderByComputedAtDesc(experiment.getId(), "texture"))
                .thenReturn(Optional.empty());
        when(captures.findAllByExperiment_IdOrderByTakenAtAsc(experiment.getId())).thenReturn(List.of());
        when(calibrations.findByExperiment_IdAndConcern(experiment.getId(), "texture")).thenReturn(Optional.empty());
        when(verdicts.save(any(VerdictEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        VerdictService service = new VerdictService(experiments, captures, calibrations, verdicts, new VerdictEngine());

        VerdictResponse response = service.getLatest(experiment.getId(), workspaceId);

        assertEquals("texture", response.concern());
        assertEquals(VerdictLabel.INCONCLUSIVE, response.label());
        assertEquals(3.0, response.noiseFloor());
        verify(calibrations).save(any(NoiseCalibrationEntity.class));
        verify(verdicts).save(any(VerdictEntity.class));
    }

    @Test
    void workspaceMismatchCannotReadOrComputeVerdict() {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        CaptureRepository captures = org.mockito.Mockito.mock(CaptureRepository.class);
        NoiseCalibrationRepository calibrations = org.mockito.Mockito.mock(NoiseCalibrationRepository.class);
        VerdictRepository verdicts = org.mockito.Mockito.mock(VerdictRepository.class);
        UUID experimentId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(experiments.findByIdAndWorkspaceId(experimentId, workspaceId)).thenReturn(Optional.empty());
        VerdictService service = new VerdictService(experiments, captures, calibrations, verdicts, new VerdictEngine());

        assertThrows(ResponseStatusException.class, () -> service.getLatest(experimentId, workspaceId));

        verify(captures, never()).findAllByExperiment_IdOrderByTakenAtAsc(any());
        verify(verdicts, never()).save(any(VerdictEntity.class));
    }

    @Test
    void targetConcernIsConvertedFromExperimentCodeToProviderAction() {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        CaptureRepository captures = org.mockito.Mockito.mock(CaptureRepository.class);
        NoiseCalibrationRepository calibrations = org.mockito.Mockito.mock(NoiseCalibrationRepository.class);
        VerdictRepository verdicts = org.mockito.Mockito.mock(VerdictRepository.class);
        UUID workspaceId = UUID.randomUUID();
        ExperimentEntity experiment = new ExperimentEntity(workspaceId, new CreateExperimentRequest(
                "Daily serum", "ACNE", LocalDate.now(), 8, ""));
        when(experiments.findByIdAndWorkspaceId(experiment.getId(), workspaceId)).thenReturn(Optional.of(experiment));
        when(verdicts.findFirstByExperiment_IdAndConcernOrderByComputedAtDesc(experiment.getId(), "acne"))
                .thenReturn(Optional.empty());
        when(captures.findAllByExperiment_IdOrderByTakenAtAsc(experiment.getId())).thenReturn(List.of());
        when(calibrations.findByExperiment_IdAndConcern(eq(experiment.getId()), eq("acne"))).thenReturn(Optional.empty());
        when(verdicts.save(any(VerdictEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        VerdictService service = new VerdictService(experiments, captures, calibrations, verdicts, new VerdictEngine());

        service.getLatest(experiment.getId(), workspaceId);

        verify(captures).findAllByExperiment_IdOrderByTakenAtAsc(experiment.getId());
    }

    private static ExperimentEntity experiment(UUID workspaceId) {
        return new ExperimentEntity(workspaceId, new CreateExperimentRequest(
                "Daily serum", "TEXTURE", LocalDate.now(), 8, ""));
    }
}