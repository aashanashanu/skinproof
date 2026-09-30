package com.skinproof.api.verdict;

import com.skinproof.api.experiment.ExperimentEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verdicts")
public class VerdictEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experiment_id", nullable = false)
    private ExperimentEntity experiment;

    @Column(nullable = false, length = 32)
    private String concern;

    @Column(nullable = false, length = 32)
    private String label;

    @Column(nullable = false, length = 16)
    private String confidence;

    @Column(name = "baseline_mean")
    private Double baselineMean;

    @Column(name = "current_mean")
    private Double currentMean;

    private Double delta;

    @Column(name = "noise_floor", nullable = false)
    private double noiseFloor;

    @Column(name = "baseline_capture_count", nullable = false)
    private int baselineCaptureCount;

    @Column(name = "valid_capture_count", nullable = false)
    private int validCaptureCount;

    @Column(name = "follow_up_capture_count", nullable = false)
    private int followUpCaptureCount;

    @Column(name = "elapsed_weeks", nullable = false)
    private int elapsedWeeks;

    @Column(name = "residual_noise", nullable = false)
    private boolean residualNoise;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "confound_flags_json", nullable = false, columnDefinition = "TEXT")
    private String confoundFlagsJson;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected VerdictEntity() {
    }

    public VerdictEntity(ExperimentEntity experiment, VerdictAssessment assessment, String confoundFlagsJson) {
        this.id = UUID.randomUUID();
        this.experiment = experiment;
        this.concern = assessment.concern();
        this.label = assessment.label().name();
        this.confidence = assessment.confidence().name();
        this.baselineMean = assessment.baselineMean();
        this.currentMean = assessment.currentMean();
        this.delta = assessment.delta();
        this.noiseFloor = assessment.noiseFloor();
        this.baselineCaptureCount = assessment.baselineCaptureCount();
        this.validCaptureCount = assessment.validCaptureCount();
        this.followUpCaptureCount = assessment.followUpCaptureCount();
        this.elapsedWeeks = Math.toIntExact(assessment.elapsedWeeks());
        this.residualNoise = assessment.residualNoise();
        this.explanation = assessment.explanation();
        this.confoundFlagsJson = confoundFlagsJson;
        this.computedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getConcern() { return concern; }
    public String getLabel() { return label; }
    public String getConfidence() { return confidence; }
    public Double getBaselineMean() { return baselineMean; }
    public Double getCurrentMean() { return currentMean; }
    public Double getDelta() { return delta; }
    public double getNoiseFloor() { return noiseFloor; }
    public int getBaselineCaptureCount() { return baselineCaptureCount; }
    public int getValidCaptureCount() { return validCaptureCount; }
    public int getFollowUpCaptureCount() { return followUpCaptureCount; }
    public int getElapsedWeeks() { return elapsedWeeks; }
    public boolean isResidualNoise() { return residualNoise; }
    public String getExplanation() { return explanation; }
    public String getConfoundFlagsJson() { return confoundFlagsJson; }
    public Instant getComputedAt() { return computedAt; }
}