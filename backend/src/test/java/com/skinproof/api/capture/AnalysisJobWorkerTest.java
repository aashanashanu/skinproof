package com.skinproof.api.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skinproof.api.analysis.SkinAnalysisResponse;
import com.skinproof.api.analysis.SkinAnalysisScore;
import com.skinproof.api.analysis.SkinConcern;
import com.skinproof.api.analysis.YouCamSkinAnalysisService;
import com.skinproof.api.experiment.CreateExperimentRequest;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.verdict.VerdictService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisJobWorkerTest {
    @Test
    void verdictPersistenceFailureDoesNotRetrySuccessfulProviderAnalysis() {
        CaptureAnalysisService captures = mock(CaptureAnalysisService.class);
        AnalysisRepository analyses = mock(AnalysisRepository.class);
        PrivateImageStore images = mock(PrivateImageStore.class);
        YouCamSkinAnalysisService youCam = mock(YouCamSkinAnalysisService.class);
        VerdictService verdicts = mock(VerdictService.class);
        CaptureMaskService masks = mock(CaptureMaskService.class);
        ExperimentEntity experiment = new ExperimentEntity(UUID.randomUUID(), new CreateExperimentRequest(
                "Daily serum", "ACNE", LocalDate.now(), 8, ""));
        AnalysisEntity job = new AnalysisEntity("cache-key", "acne", 9);
        job.startAttempt();
        CaptureEntity capture = new CaptureEntity(experiment, job, "experiment/captures/image",
                "image-hash", "image/png", 100, "[]", true, true);

        when(captures.claimNext()).thenReturn(job);
        when(captures.firstCaptureForAnalysis(job.getId())).thenReturn(capture);
        when(captures.concernsFor(job)).thenReturn(List.of(SkinConcern.ACNE));
        when(images.get(capture.getImageKey())).thenReturn(new byte[] {1, 2, 3});
        when(youCam.analyze(any(), any(), any(), any())).thenReturn(new SkinAnalysisResponse(
                "task-1", "SD", 9, List.of(new SkinAnalysisScore("acne", 60.0, 60, List.of()))));
        org.mockito.Mockito.doThrow(new IllegalStateException("verdict database unavailable"))
                .when(verdicts).recomputeForAnalysis(job.getId());
        AnalysisJobWorker worker = new AnalysisJobWorker(captures, analyses, images, youCam, verdicts, masks);

        worker.processOneReadyJob();

        assertEquals(AnalysisStatus.SUCCEEDED, job.getStatus());
        verify(captures).saveAnalysis(job);
        verify(captures, never()).maxAttempts();
        verify(youCam).analyze(any(), any(), any(), any());
        verify(masks).archive(job.getId(), List.of(new SkinAnalysisScore("acne", 60.0, 60, List.of())));
    }
}