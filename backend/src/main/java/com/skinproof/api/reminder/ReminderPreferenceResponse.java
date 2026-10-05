package com.skinproof.api.reminder;

import java.time.LocalTime;

public record ReminderPreferenceResponse(
        boolean enabled,
        String timezone,
        int weekday,
        LocalTime preferredTime,
        ReminderCadence cadence) {
    public static ReminderPreferenceResponse defaults() {
        return new ReminderPreferenceResponse(false, "America/Chicago", 1, LocalTime.of(9, 0), ReminderCadence.WEEKLY);
    }
}