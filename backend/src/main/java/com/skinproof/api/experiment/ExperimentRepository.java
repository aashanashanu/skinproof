package com.skinproof.api.experiment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentRepository extends JpaRepository<ExperimentEntity, UUID> {
    List<ExperimentEntity> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<ExperimentEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}