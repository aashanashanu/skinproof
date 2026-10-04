package com.skinproof.api.experiment;

import com.skinproof.api.auth.OwnerType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ExperimentRepository extends JpaRepository<ExperimentEntity, UUID> {
    List<ExperimentEntity> findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(OwnerType ownerType, UUID ownerId);

    Optional<ExperimentEntity> findByIdAndOwnerTypeAndOwnerId(UUID id, OwnerType ownerType, UUID ownerId);

    List<ExperimentEntity> findAllByOwnerTypeAndOwnerId(OwnerType ownerType, UUID ownerId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update ExperimentEntity e set e.ownerType = com.skinproof.api.auth.OwnerType.USER, e.ownerId = :userId, e.workspaceId = null where e.ownerType = com.skinproof.api.auth.OwnerType.DEMO and e.workspaceId = :workspaceId")
    int claimDemoWorkspace(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Query("select e from ExperimentEntity e where e.id = :id and e.ownerType = com.skinproof.api.auth.OwnerType.DEMO and e.workspaceId = :workspaceId")
    Optional<ExperimentEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @org.springframework.data.jpa.repository.Query("select e from ExperimentEntity e where e.ownerType = com.skinproof.api.auth.OwnerType.DEMO and e.workspaceId = :workspaceId order by e.createdAt desc")
    List<ExperimentEntity> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
}