package com.skinproof.api.verdict;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoiseCalibrationRepository extends JpaRepository<NoiseCalibrationEntity, UUID> {
    Optional<NoiseCalibrationEntity> findByExperiment_IdAndConcern(UUID experimentId, String concern);
}