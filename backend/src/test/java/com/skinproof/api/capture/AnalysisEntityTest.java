package com.skinproof.api.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnalysisEntityTest {
    @Test
    void manualRetryResetsAttemptBudget() {
        AnalysisEntity analysis = new AnalysisEntity("cache-key", "acne", 9);
        analysis.startAttempt();
        analysis.fail("PROVIDER_ERROR", "temporary failure");

        analysis.queueRetry();

        assertEquals(AnalysisStatus.QUEUED, analysis.getStatus());
        assertEquals(0, analysis.getAttemptCount());
    }
}