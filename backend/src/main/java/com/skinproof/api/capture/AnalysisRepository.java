package com.skinproof.api.capture;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisRepository extends JpaRepository<AnalysisEntity, UUID> {
    Optional<AnalysisEntity> findByCacheKey(String cacheKey);

    @Query("select a from AnalysisEntity a where a.status in :statuses and a.retryAfter <= :now order by a.createdAt asc")
    List<AnalysisEntity> findRunnable(@Param("statuses") List<AnalysisStatus> statuses, @Param("now") Instant now);

    @Query("select count(distinct c.analysis.id) from CaptureEntity c where c.experiment.id = :experimentId and c.takenAt >= :since and c.analysis.status in :statuses")
    long countDailyAttempts(
            @Param("experimentId") UUID experimentId,
            @Param("since") Instant since,
            @Param("statuses") List<AnalysisStatus> statuses);
}