package com.skinproof.api.capture;

import java.util.List;

public record CaptureQualityAssessment(int score, List<String> flags, boolean valid) {
}