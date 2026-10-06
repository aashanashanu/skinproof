package com.skinproof.api.experiment;

import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.OwnerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "experiments")
public class ExperimentEntity {
    @Id
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(name = "target_concern", nullable = false, length = 32)
    private String targetConcern;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "duration_weeks", nullable = false)
    private int durationWeeks;

    @Column(name = "protocol_notes", nullable = false, length = 500)
    private String protocolNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExperimentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExperimentEntity() {
    }

    public ExperimentEntity(UUID workspaceId, CreateExperimentRequest request) {
        this(OwnerScope.demo(workspaceId), request);
    }

    public ExperimentEntity(OwnerScope owner, CreateExperimentRequest request) {
        this.id = UUID.randomUUID();
        this.ownerType = owner.type();
        this.ownerId = owner.id();
        this.workspaceId = owner.type() == OwnerType.DEMO ? owner.id() : null;
        this.productName = request.productName().trim();
        this.targetConcern = request.targetConcern();
        this.startDate = request.startDate();
        this.durationWeeks = request.durationWeeks();
        this.protocolNotes = request.protocolNotes() == null ? "" : request.protocolNotes().trim();
        this.status = ExperimentStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public OwnerType getOwnerType() { return ownerType; }
    public UUID getOwnerId() { return ownerId; }

    public void transferDemoDataTo(OwnerScope owner) {
        if (ownerType != OwnerType.DEMO || owner.type() != OwnerType.USER) {
            throw new IllegalStateException("Only demo-owned experiments can be claimed by a user.");
        }
        ownerType = owner.type();
        ownerId = owner.id();
        workspaceId = null;
    }

    public String getProductName() {
        return productName;
    }

    public String getTargetConcern() {
        return targetConcern;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public int getDurationWeeks() {
        return durationWeeks;
    }

    public String getProtocolNotes() {
        return protocolNotes;
    }

    public ExperimentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void archive() {
        this.status = ExperimentStatus.ARCHIVED;
    }

    public void restore() {
        this.status = ExperimentStatus.ACTIVE;
    }
}