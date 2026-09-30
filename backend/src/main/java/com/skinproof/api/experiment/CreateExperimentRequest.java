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
        @NotBlank @Pattern(regexp = "WRINKLE|DROOPY_UPPER_EYELID|DROOPY_LOWER_EYELID|FIRMNESS|ACNE|MOISTURE|EYE_BAG|DARK_CIRCLE_V2|AGE_SPOT|RADIANCE|REDNESS|OILINESS|PORE|TEXTURE|TEAR_TROUGH|SKIN_TYPE") String targetConcern,
        @NotNull LocalDate startDate,
        @Min(1) @Max(52) int durationWeeks,
        @Size(max = 500) String protocolNotes) {
}