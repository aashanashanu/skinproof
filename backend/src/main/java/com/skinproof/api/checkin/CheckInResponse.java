package com.skinproof.api.checkin;

import com.skinproof.api.verdict.ConfoundCode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CheckInResponse(
        UUID checkInId,
        LocalDate checkInDate,
        List<ConfoundCode> reportedConfounds,
        List<CheckInSuggestion> automaticSuggestions,
        List<ConfoundCode> confirmedSuggestions,
        boolean simulated) {
}
