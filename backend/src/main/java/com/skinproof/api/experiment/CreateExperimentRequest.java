package com.skinproof.api.experiment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateExperimentRequest(
        @NotBlank @Size(max = 120) String productName,
        @NotBlank @Pattern(regexp = "ACNE|AGE_SPOT|TEXTURE|REDNESS") String targetConcern,
        @NotNull LocalDate startDate,
        @Min(1) @Max(52) int durationWeeks,
        @Size(max = 500) String protocolNotes) {
}