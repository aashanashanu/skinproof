package com.skinproof.api.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.experiment.CreateExperimentRequest;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.verdict.VerdictService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CaptureBaselineServiceTest {
    private final CaptureRepository captures = mock(CaptureRepository.class);
    private final VerdictService verdicts = mock(VerdictService.class);
    private final CaptureBaselineService service = new CaptureBaselineService(captures, verdicts);
    private final OwnerScope owner = OwnerScope.demo(UUID.randomUUID());

    @Test
    void promotesExistingCaptureWithoutChangingItsImageAnalysisOrTimestamp() {
        CaptureEntity capture = capture(false, true, AnalysisStatus.SUCCEEDED);
        var takenAt = capture.getTakenAt();
        var analysis = capture.getAnalysis();
        stubCapture(capture);

        CaptureResponse response = service.markAsBaseline(capture.getId(), owner);

        assertTrue(response.baseline());
        assertEquals(capture.getId(), response.captureId());
        assertEquals(takenAt, capture.getTakenAt());
        assertEquals(analysis, capture.getAnalysis());
        assertEquals(AnalysisStatus.SUCCEEDED, analysis.getStatus());
        assertEquals("private-image", capture.getImageKey());
        verify(captures).saveAndFlush(capture);
        verify(verdicts).recomputeWithExistingConfounds(capture.getExperiment().getId(), owner);
        verify(captures, never()).findAllByExperiment_IdOrderByTakenAtAsc(any());
    }

    @Test
    void repeatedSelectionDoesNotWriteOrRecompute() {
        CaptureEntity capture = capture(true, true, AnalysisStatus.SUCCEEDED);
        stubCapture(capture);

        assertTrue(service.markAsBaseline(capture.getId(), owner).baseline());

        verify(captures, never()).saveAndFlush(any());
        verifyNoInteractions(verdicts);
    }

    @Test
    void signedInOwnerUsesAccountScopedLookup() {
        OwnerScope account = OwnerScope.user(UUID.randomUUID());
        CaptureEntity capture = capture(false, true, AnalysisStatus.SUCCEEDED);
        when(captures.findByIdAndOwnerTypeAndOwnerId(capture.getId(), account.type(), account.id()))
                .thenReturn(Optional.of(capture));

        assertTrue(service.markAsBaseline(capture.getId(), account).baseline());

        verify(captures, never()).findByIdAndWorkspaceId(any(), any());
        verify(verdicts).recomputeWithExistingConfounds(capture.getExperiment().getId(), account);
    }

    @Test
    void anotherOwnersCaptureIsNotFound() {
        UUID captureId = UUID.randomUUID();
        when(captures.findByIdAndWorkspaceId(captureId, owner.id())).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.markAsBaseline(captureId, owner));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(captures, never()).saveAndFlush(any());
        verifyNoInteractions(verdicts);
    }

    @Test
    void invalidCaptureCannotBeBaseline() {
        assertRejected(capture(false, false, AnalysisStatus.SUCCEEDED));
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisStatus.class, names = "SUCCEEDED", mode = EnumSource.Mode.EXCLUDE)
    void incompleteOrFailedAnalysisCannotBeBaseline(AnalysisStatus status) {
        assertRejected(capture(false, true, status));
    }

    private void assertRejected(CaptureEntity capture) {
        stubCapture(capture);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.markAsBaseline(capture.getId(), owner));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertFalse(capture.isBaseline());
        verify(captures, never()).saveAndFlush(any());
        verifyNoInteractions(verdicts);
    }

    private void stubCapture(CaptureEntity capture) {
        when(captures.findByIdAndWorkspaceId(capture.getId(), owner.id())).thenReturn(Optional.of(capture));
    }

    private CaptureEntity capture(boolean baseline, boolean valid, AnalysisStatus status) {
        ExperimentEntity experiment = new ExperimentEntity(owner.id(), new CreateExperimentRequest(
                "Barrier serum", "TEXTURE", LocalDate.now(), 8, ""));
        AnalysisEntity analysis = mock(AnalysisEntity.class);
        when(analysis.getId()).thenReturn(UUID.randomUUID());
        when(analysis.getStatus()).thenReturn(status);
        return new CaptureEntity(experiment, analysis, "private-image", "hash", "image/png", 100,
                "[]", baseline, valid);
    }
}