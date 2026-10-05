package com.skinproof.api.reminder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record ReminderPreferenceRequest(
        boolean enabled,
        @NotBlank @Size(max = 64) String timezone,
        @Min(1) @Max(7) int weekday,
        @NotNull LocalTime preferredTime,
        @NotNull ReminderCadence cadence) {
}