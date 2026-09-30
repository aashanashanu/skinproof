package com.skinproof.api.capture;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaptureRepository extends JpaRepository<CaptureEntity, UUID> {
    Optional<CaptureEntity> findFirstByAnalysisIdOrderByTakenAtAsc(UUID analysisId);

    java.util.List<CaptureEntity> findAllByExperiment_IdOrderByTakenAtAsc(UUID experimentId);

    java.util.List<CaptureEntity> findAllByAnalysis_Id(UUID analysisId);

    @Query("select c from CaptureEntity c join fetch c.analysis a join c.experiment e where c.id = :captureId and e.workspaceId = :workspaceId")
    Optional<CaptureEntity> findByIdAndWorkspaceId(
            @Param("captureId") UUID captureId,
            @Param("workspaceId") UUID workspaceId);
}