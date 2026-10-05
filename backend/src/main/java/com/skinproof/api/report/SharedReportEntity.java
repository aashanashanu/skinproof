package com.skinproof.api.report;

import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.OwnerType;
import com.skinproof.api.experiment.ExperimentEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shared_reports")
public class SharedReportEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experiment_id", nullable = false)
    private ExperimentEntity experiment;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "image_keys_json", nullable = false, columnDefinition = "TEXT")
    private String imageKeysJson;

    @Column(name = "include_images", nullable = false)
    private boolean includeImages;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected SharedReportEntity() {
    }

    public SharedReportEntity(
            UUID id,
            ExperimentEntity experiment,
            OwnerScope owner,
            String tokenHash,
            String snapshotJson,
            String imageKeysJson,
            boolean includeImages,
            Instant createdAt,
            Instant expiresAt) {
        this.id = id;
        this.experiment = experiment;
        this.ownerType = owner.type();
        this.ownerId = owner.id();
        this.tokenHash = tokenHash;
        this.snapshotJson = snapshotJson;
        this.imageKeysJson = imageKeysJson;
        this.includeImages = includeImages;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) revokedAt = now;
    }

    public UUID getId() { return id; }
    public ExperimentEntity getExperiment() { return experiment; }
    public OwnerType getOwnerType() { return ownerType; }
    public UUID getOwnerId() { return ownerId; }
    public String getTokenHash() { return tokenHash; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getImageKeysJson() { return imageKeysJson; }
    public boolean isIncludeImages() { return includeImages; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}