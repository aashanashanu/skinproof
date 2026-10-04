package com.skinproof.api.checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skinproof.api.analysis.SkinConcern;
import com.skinproof.api.capture.AnalysisEntity;
import com.skinproof.api.capture.AnalysisStatus;
import com.skinproof.api.capture.CaptureEntity;
import com.skinproof.api.capture.CaptureRepository;
import com.skinproof.api.experiment.CreateExperimentRequest;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.experiment.ExperimentRepository;
import com.skinproof.api.verdict.ConfoundCode;
import com.skinproof.api.verdict.ConfoundFlag;
import com.skinproof.api.verdict.VerdictService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class CheckInServiceTest {
    @Test
    void captureGapSuggestionRequiresMoreThanFourteenDaysAndDoesNotApplyUntilConfirmed() {
        LocalDate checkInDate = LocalDate.of(2026, 10, 4);
        Fixture exactBoundary = new Fixture(checkInDate);
        exactBoundary.captures.add(exactBoundary.capture(checkInDate.minusDays(13), false, 80));

        CheckInResponse boundaryResponse = exactBoundary.service.save(
                exactBoundary.experiment.getId(), exactBoundary.workspaceId,
                new CheckInRequest(checkInDate, List.of(ConfoundCode.STRESS), List.of()));

        assertTrue(boundaryResponse.automaticSuggestions().stream()
                .noneMatch(suggestion -> suggestion.code() == ConfoundCode.CAPTURE_GAP));
        ArgumentCaptor<List<ConfoundFlag>> boundaryFlags = ArgumentCaptor.forClass(List.class);
        verify(exactBoundary.verdicts).recompute(
                org.mockito.ArgumentMatchers.eq(exactBoundary.experiment.getId()),
                org.mockito.ArgumentMatchers.eq(exactBoundary.workspaceId),
                boundaryFlags.capture());
        assertEquals(List.of(ConfoundCode.STRESS), boundaryFlags.getValue().stream().map(ConfoundFlag::code).toList());

        Fixture overBoundary = new Fixture(checkInDate);
        overBoundary.captures.add(overBoundary.capture(checkInDate.minusDays(14), false, 80));
        CheckInResponse suggested = overBoundary.service.save(
                overBoundary.experiment.getId(), overBoundary.workspaceId,
                new CheckInRequest(checkInDate, List.of(), List.of()));

        assertTrue(suggested.automaticSuggestions().stream()
                .anyMatch(suggestion -> suggestion.code() == ConfoundCode.CAPTURE_GAP));
        ArgumentCaptor<List<ConfoundFlag>> unconfirmedFlags = ArgumentCaptor.forClass(List.class);
        verify(overBoundary.verdicts).recompute(
                org.mockito.ArgumentMatchers.eq(overBoundary.experiment.getId()),
                org.mockito.ArgumentMatchers.eq(overBoundary.workspaceId),
                unconfirmedFlags.capture());
        assertTrue(unconfirmedFlags.getValue().isEmpty());
    }

    @Test
    void confirmsQualityDriftOnlyAfterAtLeastTwoFollowUps() {
        LocalDate date = LocalDate.of(2026, 10, 4);
        Fixture fixture = new Fixture(date);
        fixture.captures.add(fixture.capture(date.minusDays(10), true, 100));
        fixture.captures.add(fixture.capture(date.minusDays(3), false, 90));
        fixture.captures.add(fixture.capture(date.minusDays(2), false, 90));

        CheckInResponse response = fixture.service.save(
                fixture.experiment.getId(), fixture.workspaceId,
                new CheckInRequest(date, List.of(), List.of(ConfoundCode.QUALITY_DRIFT)));

        CheckInSuggestion suggestion = response.automaticSuggestions().stream()
                .filter(item -> item.code() == ConfoundCode.QUALITY_DRIFT)
                .findFirst()
                .orElseThrow();
        assertTrue(suggestion.reason().contains("5 points"));
        ArgumentCaptor<List<ConfoundFlag>> flags = ArgumentCaptor.forClass(List.class);
        verify(fixture.verdicts).recompute(
                org.mockito.ArgumentMatchers.eq(fixture.experiment.getId()),
                org.mockito.ArgumentMatchers.eq(fixture.workspaceId),
                flags.capture());
        assertEquals(List.of(ConfoundCode.QUALITY_DRIFT), flags.getValue().stream().map(ConfoundFlag::code).toList());
    }

    @Test
    void rejectsConfirmingAnAutomaticSuggestionThatWasNotDetected() {
        LocalDate date = LocalDate.of(2026, 10, 4);
        Fixture fixture = new Fixture(date);

        assertThrows(ResponseStatusException.class, () -> fixture.service.save(
                fixture.experiment.getId(), fixture.workspaceId,
                new CheckInRequest(date, List.of(), List.of(ConfoundCode.CAPTURE_GAP))));
        verify(fixture.verdicts, never()).recompute(any(UUID.class), any(UUID.class), any());
    }

    @Test
    void rejectsAutomaticFlagsSubmittedAsSelfReported() {
        LocalDate date = LocalDate.of(2026, 10, 4);
        Fixture fixture = new Fixture(date);

        assertThrows(ResponseStatusException.class, () -> fixture.service.save(
                fixture.experiment.getId(), fixture.workspaceId,
                new CheckInRequest(date, List.of(ConfoundCode.QUALITY_DRIFT), List.of())));
        verify(fixture.checkIns, never()).save(any());
    }

    @Test
    void rejectsExperimentOutsideWorkspace() {
        LocalDate date = LocalDate.of(2026, 10, 4);
        Fixture fixture = new Fixture(date);
        when(fixture.experiments.findByIdAndWorkspaceId(fixture.experiment.getId(), fixture.workspaceId))
                .thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> fixture.service.list(
                fixture.experiment.getId(), fixture.workspaceId));
        verify(fixture.checkIns, never()).findAllByExperiment_IdOrderByCheckInDateAsc(any());
    }

        @Test
        void rejectsCheckInsForSimulatedOnlyDemoExperiments() {
                LocalDate date = LocalDate.of(2026, 10, 4);
                Fixture fixture = new Fixture(date);
                AnalysisEntity analysis = new AnalysisEntity("simulated-cache-key", "texture", 0);
                analysis.succeed("SIMULATED-task", "[]");
                fixture.captures.add(CaptureEntity.simulated(
                                fixture.experiment, analysis, "simulated/key", "b".repeat(64), true,
                                date.minusDays(14).atStartOfDay(ZoneOffset.UTC).toInstant()));

                assertThrows(ResponseStatusException.class, () -> fixture.service.save(
                                fixture.experiment.getId(), fixture.workspaceId,
                                new CheckInRequest(date, List.of(ConfoundCode.STRESS), List.of())));
                verify(fixture.checkIns, never()).save(any());
                verify(fixture.verdicts, never()).recompute(any(UUID.class), any(UUID.class), any());
        }

    private static final class Fixture {
        private final UUID workspaceId = UUID.randomUUID();
        private final ExperimentEntity experiment = new ExperimentEntity(workspaceId, new CreateExperimentRequest(
                "Barrier serum", "TEXTURE", LocalDate.of(2026, 1, 1), 8, ""));
        private final ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        private final CheckInRepository checkIns = org.mockito.Mockito.mock(CheckInRepository.class);
        private final CaptureRepository capturesRepository = org.mockito.Mockito.mock(CaptureRepository.class);
        private final VerdictService verdicts = org.mockito.Mockito.mock(VerdictService.class);
        private final CheckInService service;
        private final List<CaptureEntity> captures = new ArrayList<>();
        private final List<CheckInEntity> savedCheckIns = new ArrayList<>();

        private Fixture(LocalDate checkInDate) {
            when(experiments.findByIdAndWorkspaceId(experiment.getId(), workspaceId)).thenReturn(Optional.of(experiment));
            when(capturesRepository.findAllByExperiment_IdOrderByTakenAtAsc(experiment.getId()))
                    .thenAnswer(ignored -> List.copyOf(captures));
            when(checkIns.findByExperiment_IdAndCheckInDate(experiment.getId(), checkInDate)).thenReturn(Optional.empty());
            when(checkIns.save(any(CheckInEntity.class))).thenAnswer(invocation -> {
                CheckInEntity saved = invocation.getArgument(0);
                savedCheckIns.removeIf(existing -> existing.getCheckInDate().equals(saved.getCheckInDate()));
                savedCheckIns.add(saved);
                return saved;
            });
            when(checkIns.findAllByExperiment_IdOrderByCheckInDateAsc(experiment.getId()))
                    .thenAnswer(ignored -> List.copyOf(savedCheckIns));
            service = new CheckInService(experiments, checkIns, capturesRepository, verdicts);
        }

        private CaptureEntity capture(LocalDate date, boolean baseline, int quality) {
            AnalysisEntity analysis = new AnalysisEntity(UUID.randomUUID().toString().replace("-", ""), "texture", 9);
            analysis.succeed("task-id", "[]");
            Instant takenAt = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            return new CaptureEntity(
                    experiment, analysis, "key", "a".repeat(64), "image/png", quality, "[]", baseline, true,
                    false, takenAt);
        }
    }
}
