package com.skinproof.api.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinproof.api.analysis.SkinAnalysisScore;
import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.OwnerType;
import com.skinproof.api.capture.AnalysisEntity;
import com.skinproof.api.capture.CaptureEntity;
import com.skinproof.api.capture.CaptureRepository;
import com.skinproof.api.experiment.CreateExperimentRequest;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.experiment.ExperimentRepository;
import com.skinproof.api.verdict.VerdictRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class SharedReportServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void createsSnapshotWithHashOnlyTokenAndImagesExcludedByDefault() throws Exception {
        Fixture fixture = new Fixture();
        fixture.captures.add(fixture.capture(false, 71.25));

        CreateSharedReportResponse created = fixture.service.create(
                fixture.experiment.getId(), fixture.owner, new CreateSharedReportRequest(false));
        SharedReportEntity saved = fixture.saved;

        assertEquals(43, created.token().length());
        assertNotEquals(created.token(), saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length());
        CreateSharedReportResponse second = fixture.service.create(
                fixture.experiment.getId(), fixture.owner, new CreateSharedReportRequest(false));
        assertNotEquals(created.token(), second.token());
        assertFalse(saved.isIncludeImages());
        SharedReportSnapshot snapshot = JSON.readValue(saved.getSnapshotJson(), SharedReportSnapshot.class);
        assertEquals("Barrier serum", snapshot.productName());
        assertEquals(1, snapshot.captures().size());
        assertFalse(snapshot.captures().getFirst().simulated());
        assertEquals(1, snapshot.captures().getFirst().captureNumber());
        assertEquals("{}", saved.getImageKeysJson());
        assertFalse(saved.getSnapshotJson().contains("private note"));
        assertFalse(saved.getSnapshotJson().contains("private-key"));
        assertFalse(saved.getSnapshotJson().contains("do-not-share"));
        assertFalse(saved.getSnapshotJson().contains("captureId"));
        assertFalse(saved.getSnapshotJson().contains("reportId"));
        when(fixture.reports.findByTokenHash(saved.getTokenHash())).thenReturn(Optional.of(saved));
        assertTrue(fixture.service.readPublic(created.token()).imageUrls().isEmpty());
        verify(fixture.presigner, never()).presignGetObject(any(
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class));
    }

        @Test
        void invalidTokenIsUnavailableWithoutARepositoryLookup() {
                Fixture fixture = new Fixture();

                assertThrows(ResponseStatusException.class, () -> fixture.service.readPublic("not-a-share-token"));
                verify(fixture.reports, never()).findByTokenHash(any());
        }

    @Test
    void publicReadRejectsExpiredOrRevokedLinks() {
        Fixture fixture = new Fixture();
        CreateSharedReportResponse created = fixture.service.create(
                fixture.experiment.getId(), fixture.owner, new CreateSharedReportRequest(false));
        fixture.saved.revoke(Instant.now());
        when(fixture.reports.findByTokenHash(fixture.saved.getTokenHash())).thenReturn(Optional.of(fixture.saved));

        assertThrows(ResponseStatusException.class, () -> fixture.service.readPublic(created.token()));
    }

        @Test
        void publicSnapshotOmitsSensitiveConfoundsAndInternalExplanation() {
                Fixture fixture = new Fixture();
                com.skinproof.api.verdict.VerdictEntity verdict = mock(com.skinproof.api.verdict.VerdictEntity.class);
                when(verdict.getLabel()).thenReturn("INCONCLUSIVE");
                when(verdict.getConfidence()).thenReturn("LOW");
                when(verdict.getConfoundFlagsJson()).thenReturn("[{\"code\":\"ILLNESS\"}]");
                when(verdict.getExplanation()).thenReturn("Confounds noted: ILLNESS; confidence was reduced.");
                when(fixture.verdicts.findFirstByExperiment_IdAndConcernOrderByComputedAtDesc(
                                fixture.experiment.getId(), "texture")).thenReturn(Optional.of(verdict));

                fixture.service.create(fixture.experiment.getId(), fixture.owner, new CreateSharedReportRequest(false));

                assertFalse(fixture.saved.getSnapshotJson().contains("ILLNESS"));
                assertFalse(fixture.saved.getSnapshotJson().contains("Confounds noted"));
                assertTrue(fixture.saved.getSnapshotJson().contains("cannot establish that a product caused"));
        }

    @Test
    void publicReadRejectsExpiredLinks() {
        Fixture fixture = new Fixture();
        CreateSharedReportResponse created = fixture.service.create(
                fixture.experiment.getId(), fixture.owner, new CreateSharedReportRequest(false));
        SharedReportEntity expired = new SharedReportEntity(
                UUID.randomUUID(), fixture.experiment, fixture.owner, fixture.saved.getTokenHash(),
                fixture.saved.getSnapshotJson(), "{}", false,
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        when(fixture.reports.findByTokenHash(fixture.saved.getTokenHash())).thenReturn(Optional.of(expired));

        assertThrows(ResponseStatusException.class, () -> fixture.service.readPublic(created.token()));
    }

    @Test
    void ownerCanRevokeButAnotherOwnerCannot() {
        Fixture fixture = new Fixture();
        CreateSharedReportResponse created = fixture.service.create(
                fixture.experiment.getId(), fixture.owner, new CreateSharedReportRequest(false));
        OwnerScope otherOwner = OwnerScope.user(UUID.randomUUID());
        when(fixture.reports.findByIdAndOwnerTypeAndOwnerId(
                fixture.saved.getId(), OwnerType.USER, fixture.owner.id())).thenReturn(Optional.of(fixture.saved));

        assertThrows(ResponseStatusException.class, () -> fixture.service.revoke(
                fixture.saved.getId(), otherOwner));
        fixture.service.revoke(fixture.saved.getId(), fixture.owner);
        when(fixture.reports.findByTokenHash(fixture.saved.getTokenHash())).thenReturn(Optional.of(fixture.saved));

        assertThrows(ResponseStatusException.class, () -> fixture.service.readPublic(created.token()));
        verify(fixture.reports).findByIdAndOwnerTypeAndOwnerId(
                fixture.saved.getId(), OwnerType.USER, otherOwner.id());
    }

    @Test
    void imageOptInStoresOnlyPrivateKeysAndReturnsReportLocalImageLookup() throws Exception {
        Fixture fixture = new Fixture();
        fixture.captures.add(fixture.capture(false, 71.25));
        CreateSharedReportResponse created = fixture.service.create(
                fixture.experiment.getId(), fixture.owner, new CreateSharedReportRequest(true));
        when(fixture.reports.findByTokenHash(fixture.saved.getTokenHash())).thenReturn(Optional.of(fixture.saved));
        var signedRequest = mock(software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest.class);
        when(signedRequest.url()).thenReturn(new java.net.URL("https://images.example.test/signed"));
        when(fixture.presigner.presignGetObject(any(
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .thenReturn(signedRequest);

        SharedReportResponse response = fixture.service.readPublic(created.token());

        assertTrue(fixture.saved.isIncludeImages());
        assertFalse(fixture.saved.getImageKeysJson().contains("captureId"));
        assertTrue(fixture.saved.getImageKeysJson().contains("private-key"));
        assertEquals("https://images.example.test/signed", response.imageUrls().get("1"));
        assertFalse(response.imageUrls().containsKey(fixture.captures.getFirst().getId().toString()));
    }

    @Test
    void anonymousCannotCreateSharedReport() {
        Fixture fixture = new Fixture();

        assertThrows(ResponseStatusException.class, () -> fixture.service.create(
                fixture.experiment.getId(), OwnerScope.demo(UUID.randomUUID()), new CreateSharedReportRequest(false)));
        verify(fixture.reports, never()).save(any());
    }

    @Test
    void anotherOwnerCannotCreateOrListReportsForAnExperiment() {
        Fixture fixture = new Fixture();
        OwnerScope otherOwner = OwnerScope.user(UUID.randomUUID());

        assertThrows(ResponseStatusException.class, () -> fixture.service.create(
                fixture.experiment.getId(), otherOwner, new CreateSharedReportRequest(false)));
        assertThrows(ResponseStatusException.class, () -> fixture.service.list(
                fixture.experiment.getId(), otherOwner));
        verify(fixture.reports, never()).save(any());
        verify(fixture.reports, never()).findAllByExperiment_IdAndOwnerTypeAndOwnerIdOrderByCreatedAtDesc(
                fixture.experiment.getId(), otherOwner.type(), otherOwner.id());
    }

    private static final class Fixture {
        private final OwnerScope owner = OwnerScope.user(UUID.randomUUID());
        private final ExperimentEntity experiment = new ExperimentEntity(
                owner, new CreateExperimentRequest("Barrier serum", "TEXTURE", LocalDate.of(2026, 1, 1), 8, "private note"));
        private final SharedReportRepository reports = org.mockito.Mockito.mock(SharedReportRepository.class);
        private final ExperimentRepository experiments = org.mockito.Mockito.mock(ExperimentRepository.class);
        private final CaptureRepository capturesRepository = org.mockito.Mockito.mock(CaptureRepository.class);
        private final VerdictRepository verdicts = org.mockito.Mockito.mock(VerdictRepository.class);
        private final S3Presigner presigner = org.mockito.Mockito.mock(S3Presigner.class);
        private final List<CaptureEntity> captures = new java.util.ArrayList<>();
        private SharedReportEntity saved;
        private final SharedReportService service;

        private Fixture() {
            when(experiments.findByIdAndOwnerTypeAndOwnerId(experiment.getId(), owner.type(), owner.id()))
                    .thenReturn(Optional.of(experiment));
            when(capturesRepository.findAllByExperiment_IdOrderByTakenAtAsc(experiment.getId()))
                    .thenAnswer(ignored -> List.copyOf(captures));
            when(verdicts.findFirstByExperiment_IdAndConcernOrderByComputedAtDesc(experiment.getId(), "texture"))
                    .thenReturn(Optional.empty());
            when(reports.save(any(SharedReportEntity.class))).thenAnswer(invocation -> {
                saved = invocation.getArgument(0);
                return saved;
            });
            service = new SharedReportService(
                    reports, experiments, capturesRepository, verdicts, JSON, presigner, "private-image-bucket");
        }

        private CaptureEntity capture(boolean simulated, double rawScore) {
            AnalysisEntity analysis = new AnalysisEntity(UUID.randomUUID().toString().replace("-", ""), "texture", 9);
            try {
                analysis.succeed("task", JSON.writeValueAsString(List.of(new SkinAnalysisScore("texture", rawScore, 75, List.of("https://do-not-share")))));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            Instant takenAt = LocalDate.of(2026, 2, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
            if (simulated) return CaptureEntity.simulated(experiment, analysis, "simulated-key", "a".repeat(64), false, takenAt);
            return new CaptureEntity(experiment, analysis, "private-key", "b".repeat(64), "image/png", 90, "[]", false, true, false, takenAt);
        }
    }
}
