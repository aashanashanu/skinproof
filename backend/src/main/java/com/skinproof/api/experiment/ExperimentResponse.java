package com.skinproof.api.experiment;

import java.time.LocalDate;
import java.util.UUID;

public record ExperimentResponse(
        UUID id,
        String productName,
        String targetConcern,
        LocalDate startDate,
        int durationWeeks,
        String protocolNotes,
        ExperimentStatus status) {
    public static ExperimentResponse from(ExperimentEntity experiment) {
        return new ExperimentResponse(
                experiment.getId(),
                experiment.getProductName(),
                experiment.getTargetConcern(),
                experiment.getStartDate(),
                experiment.getDurationWeeks(),
                experiment.getProtocolNotes(),
                experiment.getStatus());
    }
}