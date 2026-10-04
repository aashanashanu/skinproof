package com.skinproof.api.checkin;

import com.skinproof.api.verdict.ConfoundCode;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CheckInRequest(
        @NotNull LocalDate checkInDate,
        List<ConfoundCode> reportedConfounds,
        List<ConfoundCode> confirmedSuggestions) {
    public CheckInRequest {
        reportedConfounds = reportedConfounds == null ? List.of() : List.copyOf(reportedConfounds);
        confirmedSuggestions = confirmedSuggestions == null ? List.of() : List.copyOf(confirmedSuggestions);
    }
}
