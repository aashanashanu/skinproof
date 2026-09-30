package com.skinproof.api.verdict;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerdictRepository extends JpaRepository<VerdictEntity, UUID> {
    Optional<VerdictEntity> findFirstByExperiment_IdAndConcernOrderByComputedAtDesc(UUID experimentId, String concern);
}