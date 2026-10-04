package com.skinproof.api.checkin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.verdict.ConfoundCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "experiment_checkins")
public class CheckInEntity {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experiment_id", nullable = false)
    private ExperimentEntity experiment;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "reported_confounds_json", nullable = false, columnDefinition = "TEXT")
    private String reportedConfoundsJson;

    @Column(name = "automatic_suggestions_json", nullable = false, columnDefinition = "TEXT")
    private String automaticSuggestionsJson;

    @Column(name = "confirmed_suggestions_json", nullable = false, columnDefinition = "TEXT")
    private String confirmedSuggestionsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CheckInEntity() {
    }

    public CheckInEntity(
            ExperimentEntity experiment,
            LocalDate checkInDate,
            List<ConfoundCode> reportedConfounds,
            List<CheckInSuggestion> automaticSuggestions,
            List<ConfoundCode> confirmedSuggestions) {
        this.id = UUID.randomUUID();
        this.experiment = experiment;
        this.checkInDate = checkInDate;
        this.createdAt = Instant.now();
        update(reportedConfounds, automaticSuggestions, confirmedSuggestions);
    }

    public void update(
            List<ConfoundCode> reportedConfounds,
            List<CheckInSuggestion> automaticSuggestions,
            List<ConfoundCode> confirmedSuggestions) {
        this.reportedConfoundsJson = writeJson(reportedConfounds);
        this.automaticSuggestionsJson = writeJson(automaticSuggestions);
        this.confirmedSuggestionsJson = writeJson(confirmedSuggestions);
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ExperimentEntity getExperiment() { return experiment; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public List<ConfoundCode> getReportedConfounds() { return readCodes(reportedConfoundsJson); }
    public List<CheckInSuggestion> getAutomaticSuggestions() { return readSuggestions(automaticSuggestionsJson); }
    public List<ConfoundCode> getConfirmedSuggestions() { return readCodes(confirmedSuggestionsJson); }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize check-in data.", exception);
        }
    }

    private static List<ConfoundCode> readCodes(String json) {
        try {
            return JSON.readValue(json, JSON.getTypeFactory().constructCollectionType(List.class, ConfoundCode.class));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static List<CheckInSuggestion> readSuggestions(String json) {
        try {
            return JSON.readValue(json, JSON.getTypeFactory().constructCollectionType(List.class, CheckInSuggestion.class));
        } catch (Exception exception) {
            return List.of();
        }
    }
}
