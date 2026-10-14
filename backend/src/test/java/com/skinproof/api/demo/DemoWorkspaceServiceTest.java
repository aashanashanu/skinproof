package com.skinproof.api.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.skinproof.api.capture.AnalysisEntity;
import com.skinproof.api.capture.AnalysisRepository;
import com.skinproof.api.capture.CaptureEntity;
import com.skinproof.api.capture.CaptureRepository;
import com.skinproof.api.capture.PrivateImageStore;
import com.skinproof.api.auth.OwnerType;
import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.experiment.ExperimentRepository;
import com.skinproof.api.experiment.ExperimentResponse;
import com.skinproof.api.verdict.NoiseCalibrationRepository;
import com.skinproof.api.verdict.VerdictEngine;
import com.skinproof.api.verdict.VerdictEntity;
import com.skinproof.api.verdict.VerdictLabel;
import com.skinproof.api.verdict.VerdictRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class DemoWorkspaceServiceTest {
    @Test
    void seedRejectsNonEmptyWorkspace() {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        UUID workspaceId = UUID.randomUUID();
        when(experiments.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId))
                .thenReturn(List.of(org.mockito.Mockito.mock(ExperimentEntity.class)));
        DemoWorkspaceService service = new DemoWorkspaceService(
                experiments, null, null, null, null, null, new VerdictEngine());

        assertThrows(ResponseStatusException.class, () -> service.seed(workspaceId));
    }

    @Test
    void seedCreatesThreeLabeledExperimentsWithDistinctVerdictOutcomes() {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        CaptureRepository captures = org.mockito.Mockito.mock(CaptureRepository.class);
        AnalysisRepository analyses = org.mockito.Mockito.mock(AnalysisRepository.class);
        VerdictRepository verdicts = org.mockito.Mockito.mock(VerdictRepository.class);
        NoiseCalibrationRepository calibrations = org.mockito.Mockito.mock(NoiseCalibrationRepository.class);
        PrivateImageStore imageStore = org.mockito.Mockito.mock(PrivateImageStore.class);
        UUID workspaceId = UUID.randomUUID();

        when(experiments.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of());
        when(experiments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(analyses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(captures.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(calibrations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(verdicts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DemoWorkspaceService service = new DemoWorkspaceService(
                experiments, captures, analyses, verdicts, calibrations, imageStore, new VerdictEngine());

        List<ExperimentResponse> created = service.seed(workspaceId);

        assertEquals(3, created.size());
        assertTrue(created.stream().allMatch(experiment -> experiment.productName().contains("simulated demo")));

        ArgumentCaptor<VerdictEntity> savedVerdicts = ArgumentCaptor.forClass(VerdictEntity.class);
        verify(verdicts, times(3)).save(savedVerdicts.capture());
        List<String> labels = savedVerdicts.getAllValues().stream().map(VerdictEntity::getLabel).toList();
        assertTrue(labels.contains(VerdictLabel.IMPROVED.name()));
        assertEquals(2, labels.stream().filter(label -> label.equals(VerdictLabel.INCONCLUSIVE.name())).count());
    }

    @Test
    void resetDeletesExperimentDataAndSkipsImageDeleteForSimulatedCaptures() {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        CaptureRepository captures = org.mockito.Mockito.mock(CaptureRepository.class);
        AnalysisRepository analyses = org.mockito.Mockito.mock(AnalysisRepository.class);
        VerdictRepository verdicts = org.mockito.Mockito.mock(VerdictRepository.class);
        NoiseCalibrationRepository calibrations = org.mockito.Mockito.mock(NoiseCalibrationRepository.class);
        PrivateImageStore imageStore = org.mockito.Mockito.mock(PrivateImageStore.class);
        UUID workspaceId = UUID.randomUUID();
        ExperimentEntity experiment = org.mockito.Mockito.mock(ExperimentEntity.class);
        UUID experimentId = UUID.randomUUID();
        when(experiment.getId()).thenReturn(experimentId);
        when(experiments.findAllByOwnerTypeAndOwnerId(OwnerType.DEMO, workspaceId)).thenReturn(List.of(experiment));

        CaptureEntity simulatedCapture = org.mockito.Mockito.mock(CaptureEntity.class);
        when(simulatedCapture.isSimulated()).thenReturn(true);
        com.skinproof.api.capture.AnalysisEntity analysis = org.mockito.Mockito.mock(com.skinproof.api.capture.AnalysisEntity.class);
        UUID analysisId = UUID.randomUUID();
        when(analysis.getId()).thenReturn(analysisId);
        when(simulatedCapture.getAnalysis()).thenReturn(analysis);
        when(captures.findAllByExperiment_IdOrderByTakenAtAsc(experimentId)).thenReturn(List.of(simulatedCapture));
        when(captures.findAllByAnalysis_Id(analysisId)).thenReturn(List.of());

        DemoWorkspaceService service = new DemoWorkspaceService(
                experiments, captures, analyses, verdicts, calibrations, imageStore, new VerdictEngine());

        service.reset(workspaceId);

        verify(verdicts).deleteAllByExperiment_Id(experimentId);
        verify(calibrations).deleteAllByExperiment_Id(experimentId);
        verify(imageStore, never()).delete(any());
        verify(analyses).deleteById(analysisId);
        verify(experiments).deleteAll(List.of(experiment));
    }

    @Test
    void deleteExperimentRemovesPrivateImageAndPreservesSharedAnalysis() {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        CaptureRepository captures = org.mockito.Mockito.mock(CaptureRepository.class);
        AnalysisRepository analyses = org.mockito.Mockito.mock(AnalysisRepository.class);
        VerdictRepository verdicts = org.mockito.Mockito.mock(VerdictRepository.class);
        NoiseCalibrationRepository calibrations = org.mockito.Mockito.mock(NoiseCalibrationRepository.class);
        PrivateImageStore imageStore = org.mockito.Mockito.mock(PrivateImageStore.class);
        UUID userId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        ExperimentEntity experiment = org.mockito.Mockito.mock(ExperimentEntity.class);
        when(experiment.getId()).thenReturn(experimentId);
        when(experiments.findByIdAndOwnerTypeAndOwnerId(experimentId, OwnerType.USER, userId))
                .thenReturn(java.util.Optional.of(experiment));
        AnalysisEntity analysis = org.mockito.Mockito.mock(AnalysisEntity.class);
        when(analysis.getId()).thenReturn(analysisId);
        CaptureEntity capture = org.mockito.Mockito.mock(CaptureEntity.class);
        when(capture.isSimulated()).thenReturn(false);
        when(capture.getImageKey()).thenReturn("private/image-key");
        when(capture.getAnalysis()).thenReturn(analysis);
        CaptureEntity captureOwnedElsewhere = org.mockito.Mockito.mock(CaptureEntity.class);
        when(captures.findAllByExperiment_IdOrderByTakenAtAsc(experimentId)).thenReturn(List.of(capture));
        when(captures.findAllByAnalysis_Id(analysisId)).thenReturn(List.of(captureOwnedElsewhere));

        DemoWorkspaceService service = new DemoWorkspaceService(
                experiments, captures, analyses, verdicts, calibrations, imageStore, new VerdictEngine());

        service.deleteExperiment(experimentId, OwnerScope.user(userId));

        verify(verdicts).deleteAllByExperiment_Id(experimentId);
        verify(calibrations).deleteAllByExperiment_Id(experimentId);
        verify(imageStore).delete("private/image-key");
        verify(captures).deleteAll(List.of(capture));
        verify(captures).flush();
        verify(analyses, never()).deleteById(analysisId);
        verify(experiments).delete(experiment);
    }

    @Test
    void deletingLastCaptureDeletesRetainedDetectionMasks() {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        CaptureRepository captures = org.mockito.Mockito.mock(CaptureRepository.class);
        AnalysisRepository analyses = org.mockito.Mockito.mock(AnalysisRepository.class);
        VerdictRepository verdicts = org.mockito.Mockito.mock(VerdictRepository.class);
        NoiseCalibrationRepository calibrations = org.mockito.Mockito.mock(NoiseCalibrationRepository.class);
        PrivateImageStore imageStore = org.mockito.Mockito.mock(PrivateImageStore.class);
        UUID userId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        ExperimentEntity experiment = org.mockito.Mockito.mock(ExperimentEntity.class);
        when(experiment.getId()).thenReturn(experimentId);
        when(experiments.findByIdAndOwnerTypeAndOwnerId(experimentId, OwnerType.USER, userId))
                .thenReturn(java.util.Optional.of(experiment));
        AnalysisEntity analysis = new AnalysisEntity("cache-key", "texture", 9);
        analysis.succeed("task", """
                [{"concern":"texture","rawScore":60,"uiScore":65,"maskUrls":["https://provider.example/mask.png"]}]
                """);
        CaptureEntity capture = new CaptureEntity(experiment, analysis, "private/image-key", "hash",
                "image/png", 100, "[]", true, true);
        when(captures.findAllByExperiment_IdOrderByTakenAtAsc(experimentId)).thenReturn(List.of(capture));
        when(captures.findAllByAnalysis_Id(analysis.getId())).thenReturn(List.of());
        DemoWorkspaceService service = new DemoWorkspaceService(
                experiments, captures, analyses, verdicts, calibrations, imageStore, new VerdictEngine());

        service.deleteExperiment(experimentId, OwnerScope.user(userId));

        verify(imageStore).delete("private/image-key");
        verify(imageStore).delete("analyses/" + analysis.getId() + "/masks/texture/0.png");
        verify(analyses).deleteById(analysis.getId());
    }

    @Test
    void deleteExperimentRejectsAnExperimentOwnedByAnotherUser() {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        CaptureRepository captures = org.mockito.Mockito.mock(CaptureRepository.class);
        VerdictRepository verdicts = org.mockito.Mockito.mock(VerdictRepository.class);
        NoiseCalibrationRepository calibrations = org.mockito.Mockito.mock(NoiseCalibrationRepository.class);
        PrivateImageStore imageStore = org.mockito.Mockito.mock(PrivateImageStore.class);
        UUID otherUserId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        when(experiments.findByIdAndOwnerTypeAndOwnerId(experimentId, OwnerType.USER, otherUserId))
                .thenReturn(java.util.Optional.empty());
        DemoWorkspaceService service = new DemoWorkspaceService(
                experiments, captures, null, verdicts, calibrations, imageStore, new VerdictEngine());

        assertThrows(ResponseStatusException.class, () -> service.deleteExperiment(
                experimentId, OwnerScope.user(otherUserId)));

        verifyNoInteractions(captures, verdicts, calibrations, imageStore);
    }
}
