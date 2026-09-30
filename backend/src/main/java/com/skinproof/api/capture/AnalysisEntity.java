package com.skinproof.api.capture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analyses")
public class AnalysisEntity {
    private static final Instant TERMINAL_RETRY_TIME = Instant.parse("9999-12-31T23:59:59Z");
    @Id
    private UUID id;

    @Column(name = "cache_key", nullable = false, unique = true, length = 64)
    private String cacheKey;

    @Column(name = "concern_actions", nullable = false, length = 512)
    private String concernActions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AnalysisStatus status;

    @Column(name = "provider_task_id", length = 128)
    private String providerTaskId;

    @Column(name = "concern_scores_json", columnDefinition = "TEXT")
    private String concernScoresJson;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "documented_unit_estimate", nullable = false)
    private int documentedUnitEstimate;

    @Column(name = "retry_after", nullable = false)
    private Instant retryAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AnalysisEntity() {
    }

    public AnalysisEntity(String cacheKey, String concernActions, int documentedUnitEstimate) {
        this.id = UUID.randomUUID();
        this.cacheKey = cacheKey;
        this.concernActions = concernActions;
        this.status = AnalysisStatus.QUEUED;
        this.attemptCount = 0;
        this.documentedUnitEstimate = documentedUnitEstimate;
        this.retryAfter = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getCacheKey() { return cacheKey; }
    public String getConcernActions() { return concernActions; }
    public AnalysisStatus getStatus() { return status; }
    public String getProviderTaskId() { return providerTaskId; }
    public String getConcernScoresJson() { return concernScoresJson; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public int getAttemptCount() { return attemptCount; }
    public int getDocumentedUnitEstimate() { return documentedUnitEstimate; }
    public Instant getCreatedAt() { return createdAt; }

    public void startAttempt() {
        status = AnalysisStatus.PROCESSING;
        attemptCount++;
        errorCode = null;
        errorMessage = null;
        updatedAt = Instant.now();
    }

    public void succeed(String taskId, String scoresJson) {
        providerTaskId = taskId;
        concernScoresJson = scoresJson;
        status = AnalysisStatus.SUCCEEDED;
        retryAfter = TERMINAL_RETRY_TIME;
        errorCode = null;
        errorMessage = null;
        updatedAt = Instant.now();
    }

    public void retryAt(Instant time, String code, String message) {
        status = AnalysisStatus.RETRY_WAIT;
        retryAfter = time;
        errorCode = code;
        errorMessage = message;
        updatedAt = Instant.now();
    }

    public void fail(String code, String message) {
        status = AnalysisStatus.FAILED;
        retryAfter = TERMINAL_RETRY_TIME;
        errorCode = code;
        errorMessage = message;
        updatedAt = Instant.now();
    }

    public void queueRetry() {
        status = AnalysisStatus.QUEUED;
        attemptCount = 0;
        retryAfter = Instant.now();
        errorCode = null;
        errorMessage = null;
        updatedAt = Instant.now();
    }
}