package com.skinproof.api.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerdictEngineTest {
    private static final Instant DAY_ZERO = Instant.parse("2026-01-01T00:00:00Z");
    private final VerdictEngine engine = new VerdictEngine();

    @Test
    void positiveRawScoreDirectionProducesImprovedVerdict() {
        VerdictAssessment result = engine.assess("acne", fourCaptures(50, 52, 64, 66), List.of());

        assertEquals(VerdictLabel.IMPROVED, result.label());
        assertTrue(result.delta() > 0);
    }

    @Test
    void negativeDirectionProducesWorseVerdict() {
        VerdictAssessment result = engine.assess("acne", fourCaptures(70, 72, 50, 48), List.of());

        assertEquals(VerdictLabel.WORSE, result.label());
        assertTrue(result.delta() < 0);
    }

    @Test
    void changeInsideNoiseFloorIsNoDetectableChange() {
        VerdictAssessment result = engine.assess("acne", fourCaptures(50, 52, 51, 52), List.of());

        assertEquals(VerdictLabel.NO_DETECTABLE_CHANGE, result.label());
    }

    @Test
    void missingEvidenceIsInconclusive() {
        VerdictAssessment result = engine.assess("acne", List.of(), List.of());

        assertEquals(VerdictLabel.INCONCLUSIVE, result.label());
        assertTrue(result.explanation().contains("four valid captures"));
    }

    @Test
    void oneBaselineCaptureIsInsufficient() {
        VerdictAssessment result = engine.assess("acne", List.of(
                sample(0, true, 50), sample(28, false, 65), sample(35, false, 66), sample(42, false, 67)), List.of());

        assertEquals(VerdictLabel.INCONCLUSIVE, result.label());
        assertTrue(result.explanation().contains("two valid baseline"));
    }

    @Test
    void oneFollowUpCaptureIsInsufficient() {
        VerdictAssessment result = engine.assess("acne", List.of(
                sample(0, true, 50), sample(0, true, 52), sample(28, false, 65), sample(35, true, 51)), List.of());

        assertEquals(VerdictLabel.INCONCLUSIVE, result.label());
        assertTrue(result.explanation().contains("two valid follow-up"));
    }

    @Test
    void fourValidCapturesAreRequired() {
        VerdictAssessment result = engine.assess("acne", List.of(
                sample(0, true, 50), sample(0, true, 52), sample(28, false, 65)), List.of());

        assertEquals(VerdictLabel.INCONCLUSIVE, result.label());
        assertTrue(result.explanation().contains("four valid captures"));
    }

    @Test
    void fourWeeksAreRequired() {
        VerdictAssessment result = engine.assess("acne", List.of(
                sample(0, true, 50), sample(0, true, 52), sample(20, false, 65), sample(27, false, 66)), List.of());

        assertEquals(VerdictLabel.INCONCLUSIVE, result.label());
        assertTrue(result.explanation().contains("four weeks"));
    }

    @Test
    void exactlyFourWeeksMeetsElapsedTimeMinimum() {
        VerdictAssessment result = engine.assess("acne", List.of(
            sample(0, true, 50), sample(0, true, 52), sample(28, false, 64), sample(28, false, 66)), List.of());

        assertEquals(4, result.elapsedWeeks());
        assertEquals(VerdictLabel.IMPROVED, result.label());
    }

    @Test
    void baselineNoiseUsesPairedDifferenceAndConcernMinimum() {
        VerdictAssessment result = engine.assess("acne", fourCaptures(50, 56, 62, 64), List.of());

        assertEquals(6.0, result.noiseFloor());
        assertFalse(result.residualNoise());
    }

    @Test
    void minimumNoiseFloorPreventsZeroNoise() {
        VerdictAssessment result = engine.assess("texture", fourCaptures(50, 50, 52, 52), List.of());

        assertEquals(3.0, result.noiseFloor());
    }

    @Test
    void fiveValidCapturesSwitchNoiseToLinearFitResiduals() {
        List<CaptureMeasurement> samples = List.of(
                sample(0, true, 50), sample(0, true, 52), sample(28, false, 60),
                sample(35, false, 66), sample(42, false, 72));

        VerdictAssessment result = engine.assess("acne", samples, List.of());

        assertTrue(result.residualNoise());
        assertTrue(result.noiseFloor() >= 3.0);
        assertEquals(5, result.validCaptureCount());
    }

    @Test
    void currentMeanUsesLatestThreeFollowUps() {
        List<CaptureMeasurement> samples = List.of(
                sample(0, true, 40), sample(0, true, 42), sample(28, false, 50),
                sample(35, false, 60), sample(42, false, 70), sample(49, false, 80));

        VerdictAssessment result = engine.assess("acne", samples, List.of());

        assertEquals(70.0, result.currentMean());
    }

    @Test
    void simulatedCapturesDoNotCountAsEvidence() {
        List<CaptureMeasurement> samples = new ArrayList<>(fourCaptures(50, 52, 64, 66));
        samples.add(new CaptureMeasurement("simulated", DAY_ZERO.plusSeconds(50L * 86_400), false, true, true, 90.0));

        VerdictAssessment result = engine.assess("acne", samples, List.of());

        assertEquals(4, result.validCaptureCount());
    }

    @Test
    void invalidAndOutOfRangeScoresDoNotCount() {
        List<CaptureMeasurement> samples = new ArrayList<>(fourCaptures(50, 52, 64, 66));
        samples.add(new CaptureMeasurement("invalid", DAY_ZERO.plusSeconds(50L * 86_400), false, false, false, 90.0));
        samples.add(sample(51, false, 101));

        VerdictAssessment result = engine.assess("acne", samples, List.of());

        assertEquals(4, result.validCaptureCount());
    }

    @Test
    void repeatedImageHashDoesNotIncreaseEvidenceCount() {
        List<CaptureMeasurement> samples = List.of(
                new CaptureMeasurement("same-image", DAY_ZERO, true, true, false, 50.0),
                new CaptureMeasurement("second-baseline", DAY_ZERO.plusSeconds(60), true, true, false, 52.0),
                new CaptureMeasurement("same-image", DAY_ZERO.plusSeconds(28L * 86_400), false, true, false, 50.0),
                sample(35, false, 66));

        VerdictAssessment result = engine.assess("acne", samples, List.of());

        assertEquals(3, result.validCaptureCount());
        assertEquals(VerdictLabel.INCONCLUSIVE, result.label());
    }

    @Test
    void categoricalSkinTypeDoesNotReceiveNumericVerdict() {
        VerdictAssessment result = engine.assess("skin_type", fourCaptures(50, 52, 64, 66), List.of());

        assertEquals(VerdictLabel.INCONCLUSIVE, result.label());
        assertNull(result.baselineMean());
        assertTrue(result.explanation().contains("numeric score"));
    }

    @Test
    void oneMinorConfoundDowngradesConfidenceOneLevel() {
        VerdictAssessment result = engine.assess("acne", fourCaptures(50, 52, 64, 66),
                List.of(new ConfoundFlag(ConfoundCode.CAPTURE_GAP)));

        assertEquals(ConfidenceLevel.MEDIUM, result.confidence());
        assertTrue(result.explanation().contains("CAPTURE_GAP"));
    }

    @Test
    void eachConfoundLowersConfidenceOneLevel() {
        VerdictAssessment result = engine.assess("acne", fourCaptures(50, 52, 64, 66), List.of(
                new ConfoundFlag(ConfoundCode.CAPTURE_GAP),
                new ConfoundFlag(ConfoundCode.QUALITY_DRIFT)));

        assertEquals(ConfidenceLevel.LOW, result.confidence());
    }

    @Test
    void repeatedConfoundCodeOnlyDowngradesConfidenceOnce() {
        VerdictAssessment result = engine.assess("acne", fourCaptures(50, 52, 64, 66), List.of(
                new ConfoundFlag(ConfoundCode.CAPTURE_GAP),
                new ConfoundFlag(ConfoundCode.CAPTURE_GAP)));

        assertEquals(ConfidenceLevel.MEDIUM, result.confidence());
        assertEquals(1, result.confounds().size());
    }

    @Test
    void twoMajorConfoundsForceInconclusive() {
        VerdictAssessment result = engine.assess("acne", fourCaptures(50, 52, 64, 66), List.of(
                new ConfoundFlag(ConfoundCode.NEW_PRODUCT),
                new ConfoundFlag(ConfoundCode.ILLNESS)));

        assertEquals(VerdictLabel.INCONCLUSIVE, result.label());
        assertEquals(ConfidenceLevel.LOW, result.confidence());
    }

    @Test
    void exactTwoTimesNoiseBoundaryRequiresAndAcceptsAgreeingTrend() {
        VerdictAssessment result = engine.assess("acne", fourCaptures(50, 52, 57, 57), List.of());

        assertEquals(3.0, result.noiseFloor());
        assertEquals(6.0, result.delta());
        assertEquals(VerdictLabel.IMPROVED, result.label());
    }

    @Test
    void exactOneTimesNoiseBoundaryIsInconclusiveNotUnchanged() {
        VerdictAssessment result = engine.assess("acne", fourCaptures(50, 50, 53, 53), List.of());

        assertEquals(3.0, result.noiseFloor());
        assertEquals(3.0, result.delta());
        assertEquals(VerdictLabel.INCONCLUSIVE, result.label());
    }

    @Test
    void explanationsAvoidMedicalClaims() {
        VerdictAssessment result = engine.assess("acne", fourCaptures(50, 52, 64, 66), List.of());
        String copy = result.explanation().toLowerCase();

        assertFalse(copy.contains("cure"));
        assertFalse(copy.contains("treat"));
        assertFalse(copy.contains("diagnos"));
        assertFalse(copy.contains("proves"));
    }

    private static List<CaptureMeasurement> fourCaptures(double baselineOne, double baselineTwo, double currentOne, double currentTwo) {
        return List.of(
                sample(0, true, baselineOne),
                sample(0, true, baselineTwo),
                sample(28, false, currentOne),
                sample(35, false, currentTwo));
    }

    private static CaptureMeasurement sample(int day, boolean baseline, double score) {
        return new CaptureMeasurement("sample-" + day + "-" + baseline + "-" + score,
                DAY_ZERO.plusSeconds(day * 86_400L), baseline, true, false, score);
    }
}