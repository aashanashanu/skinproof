package com.skinproof.api.report;

import com.skinproof.api.auth.OwnerType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedReportRepository extends JpaRepository<SharedReportEntity, UUID> {
    Optional<SharedReportEntity> findByTokenHash(String tokenHash);

    Optional<SharedReportEntity> findByIdAndOwnerTypeAndOwnerId(UUID id, OwnerType ownerType, UUID ownerId);

    List<SharedReportEntity> findAllByExperiment_IdAndOwnerTypeAndOwnerIdOrderByCreatedAtDesc(
            UUID experimentId, OwnerType ownerType, UUID ownerId);
}