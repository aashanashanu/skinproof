package com.skinproof.api.verdict;

import com.skinproof.api.experiment.ExperimentEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "noise_calibrations", uniqueConstraints = @UniqueConstraint(
        name = "noise_calibrations_experiment_concern_unique",
        columnNames = {"experiment_id", "concern"}))
public class NoiseCalibrationEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experiment_id", nullable = false)
    private ExperimentEntity experiment;

    @Column(nullable = false, length = 32)
    private String concern;

    @Column(name = "noise_floor", nullable = false)
    private double noiseFloor;

    @Column(name = "valid_sample_count", nullable = false)
    private int validSampleCount;

    @Column(name = "residual_based", nullable = false)
    private boolean residualBased;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NoiseCalibrationEntity() {
    }

    public NoiseCalibrationEntity(ExperimentEntity experiment, String concern) {
        this.id = UUID.randomUUID();
        this.experiment = experiment;
        this.concern = concern;
    }

    public void update(VerdictAssessment assessment) {
        this.noiseFloor = assessment.noiseFloor();
        this.validSampleCount = assessment.validCaptureCount();
        this.residualBased = assessment.residualNoise();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getConcern() { return concern; }
    public double getNoiseFloor() { return noiseFloor; }
    public int getValidSampleCount() { return validSampleCount; }
    public boolean isResidualBased() { return residualBased; }
    public Instant getUpdatedAt() { return updatedAt; }
}