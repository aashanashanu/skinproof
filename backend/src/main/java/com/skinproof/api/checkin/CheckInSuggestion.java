package com.skinproof.api.checkin;

import com.skinproof.api.verdict.ConfoundCode;
import com.skinproof.api.verdict.ConfoundSeverity;

public record CheckInSuggestion(ConfoundCode code, ConfoundSeverity severity, String reason) {
}
