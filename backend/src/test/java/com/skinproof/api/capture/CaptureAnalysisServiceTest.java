package com.skinproof.api.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skinproof.api.analysis.SkinConcern;
import com.skinproof.api.experiment.CreateExperimentRequest;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.experiment.ExperimentRepository;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class CaptureAnalysisServiceTest {
    @Test
    void duplicateImageAndConcernSetReusesExistingAnalysis() throws Exception {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        CaptureRepository captures = org.mockito.Mockito.mock(CaptureRepository.class);
        AnalysisRepository analyses = org.mockito.Mockito.mock(AnalysisRepository.class);
        PrivateImageStore imageStore = org.mockito.Mockito.mock(PrivateImageStore.class);
        UUID workspaceId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        ExperimentEntity experiment = new ExperimentEntity(workspaceId, new CreateExperimentRequest(
                "Barrier serum", "TEXTURE", LocalDate.now(), 8, ""));
        AnalysisEntity cached = new AnalysisEntity("cache-key", "acne,texture", 9);
        byte[] bytes = image();

        when(experiments.findByIdAndWorkspaceId(experimentId, workspaceId)).thenReturn(Optional.of(experiment));
        when(analyses.findByCacheKey(any())).thenReturn(Optional.of(cached));
        when(captures.save(any(CaptureEntity.class))).thenAnswer(call -> call.getArgument(0));
        CaptureAnalysisService service = new CaptureAnalysisService(
                experiments, captures, analyses, imageStore, new CaptureQualityService(), 3);

        CaptureResponse response = service.createCapture(
                experimentId,
                workspaceId,
                bytes,
                "image/png",
                new CaptureQualityRequest(true, "good", "good", "good"),
                false,
                List.of(SkinConcern.TEXTURE, SkinConcern.ACNE));

        assertEquals(AnalysisStatus.QUEUED, response.analysisStatus());
        assertEquals(100, response.qualityScore());
        assertFalse(response.qualityFlags().contains("CAMERA_QUALITY_UNVERIFIED"));
        verify(analyses).findByCacheKey(any());
        verify(analyses, never()).saveAndFlush(any(AnalysisEntity.class));
        verify(analyses, never()).countDailyAttempts(any(), any(), any());
        verify(imageStore).put(any(), any(), org.mockito.ArgumentMatchers.eq("image/png"));

        ArgumentCaptor<CaptureEntity> capture = ArgumentCaptor.forClass(CaptureEntity.class);
        verify(captures).save(capture.capture());
        assertEquals(cached.getId(), capture.getValue().getAnalysis().getId());
    }

    @Test
    void listCapturesReturnsOrderedResultsForOwningWorkspace() {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        CaptureRepository captures = org.mockito.Mockito.mock(CaptureRepository.class);
        AnalysisRepository analyses = org.mockito.Mockito.mock(AnalysisRepository.class);
        PrivateImageStore imageStore = org.mockito.Mockito.mock(PrivateImageStore.class);
        UUID workspaceId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        ExperimentEntity experiment = new ExperimentEntity(workspaceId, new CreateExperimentRequest(
                "Barrier serum", "TEXTURE", LocalDate.now(), 8, ""));
        AnalysisEntity analysis = new AnalysisEntity("cache-key", "texture", 9);
        CaptureEntity capture = new CaptureEntity(
                experiment, analysis, "key", "hash", "image/png", 100, "[]", true, true);

        when(experiments.findByIdAndWorkspaceId(experimentId, workspaceId)).thenReturn(Optional.of(experiment));
        when(captures.findAllByExperiment_IdOrderByTakenAtAsc(experimentId)).thenReturn(List.of(capture));
        CaptureAnalysisService service = new CaptureAnalysisService(
                experiments, captures, analyses, imageStore, new CaptureQualityService(), 3);

        List<CaptureResponse> response = service.listCaptures(experimentId, workspaceId);

        assertEquals(1, response.size());
        assertEquals(capture.getId(), response.getFirst().captureId());
    }

    @Test
    void listCapturesRejectsExperimentFromAnotherWorkspace() {
        ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        CaptureRepository captures = org.mockito.Mockito.mock(CaptureRepository.class);
        AnalysisRepository analyses = org.mockito.Mockito.mock(AnalysisRepository.class);
        PrivateImageStore imageStore = org.mockito.Mockito.mock(PrivateImageStore.class);
        UUID workspaceId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();

        when(experiments.findByIdAndWorkspaceId(experimentId, workspaceId)).thenReturn(Optional.empty());
        CaptureAnalysisService service = new CaptureAnalysisService(
                experiments, captures, analyses, imageStore, new CaptureQualityService(), 3);

        assertThrows(ResponseStatusException.class, () -> service.listCaptures(experimentId, workspaceId));
        verify(captures, never()).findAllByExperiment_IdOrderByTakenAtAsc(any());
    }

    private static byte[] image() throws Exception {
        BufferedImage image = new BufferedImage(480, 480, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.GRAY);
        graphics.fillRect(0, 0, 480, 480);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}