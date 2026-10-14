package com.skinproof.api.capture;

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
@Table(name = "captures")
public class CaptureEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experiment_id", nullable = false)
    private ExperimentEntity experiment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private AnalysisEntity analysis;

    @Column(name = "image_key", nullable = false, length = 512)
    private String imageKey;

    @Column(name = "image_sha256", nullable = false, length = 64)
    private String imageSha256;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "quality_score", nullable = false)
    private int qualityScore;

    @Column(name = "quality_flags_json", nullable = false, columnDefinition = "TEXT")
    private String qualityFlagsJson;

    @Column(name = "is_baseline", nullable = false)
    private boolean baseline;

    @Column(name = "is_valid", nullable = false)
    private boolean valid;

    @Column(name = "simulated", nullable = false)
    private boolean simulated;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    protected CaptureEntity() {
    }

    public CaptureEntity(
            ExperimentEntity experiment,
            AnalysisEntity analysis,
            String imageKey,
            String imageSha256,
            String contentType,
            int qualityScore,
            String qualityFlagsJson,
            boolean baseline,
            boolean valid) {
            this(experiment, analysis, imageKey, imageSha256, contentType, qualityScore, qualityFlagsJson,
                baseline, valid, false, Instant.now());
            }

            public CaptureEntity(
                ExperimentEntity experiment,
                AnalysisEntity analysis,
                String imageKey,
                String imageSha256,
                String contentType,
                int qualityScore,
                String qualityFlagsJson,
                boolean baseline,
                boolean valid,
                boolean simulated,
                Instant takenAt) {
        this.id = UUID.randomUUID();
        this.experiment = experiment;
        this.analysis = analysis;
        this.imageKey = imageKey;
        this.imageSha256 = imageSha256;
        this.contentType = contentType;
        this.qualityScore = qualityScore;
        this.qualityFlagsJson = qualityFlagsJson;
        this.baseline = baseline;
        this.valid = valid;
        this.simulated = simulated;
        this.takenAt = takenAt;
    }

    // Used only to seed labeled, backdated demo data; never for real provider captures.
    public static CaptureEntity simulated(
            ExperimentEntity experiment,
            AnalysisEntity analysis,
            String imageKey,
            String imageSha256,
            boolean baseline,
            Instant takenAt) {
        CaptureEntity capture = new CaptureEntity(
            experiment, analysis, imageKey, imageSha256, "image/png", 100, "[]", baseline, true, true, takenAt);
        return capture;
    }

    public UUID getId() { return id; }
    public ExperimentEntity getExperiment() { return experiment; }
    public AnalysisEntity getAnalysis() { return analysis; }
    public String getImageKey() { return imageKey; }
    public String getImageSha256() { return imageSha256; }
    public String getContentType() { return contentType; }
    public int getQualityScore() { return qualityScore; }
    public String getQualityFlagsJson() { return qualityFlagsJson; }
    public boolean isBaseline() { return baseline; }
    public void markAsBaseline() { baseline = true; }
    public boolean isValid() { return valid; }
    public boolean isSimulated() { return simulated; }
    public Instant getTakenAt() { return takenAt; }
}