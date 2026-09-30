package com.skinproof.api.verdict;

import com.skinproof.api.analysis.SkinConcern;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class VerdictEngine {
    private static final int MIN_VALID_CAPTURES = 4;
    private static final int MIN_BASELINE_CAPTURES = 2;
    private static final int MIN_FOLLOW_UP_CAPTURES = 2;
    private static final long MIN_ELAPSED_WEEKS = 4;
    private static final int CURRENT_WINDOW_SIZE = 3;
    private static final int RESIDUAL_UPDATE_CAPTURE_COUNT = 5;
    private static final double MINIMUM_NOISE_FLOOR = 3.0;
    private static final double IMPROVEMENT_MULTIPLIER = 2.0;
    private static final Map<String, Double> NOISE_FLOORS = Map.ofEntries(
            Map.entry("wrinkle", MINIMUM_NOISE_FLOOR),
            Map.entry("droopy_upper_eyelid", MINIMUM_NOISE_FLOOR),
            Map.entry("droopy_lower_eyelid", MINIMUM_NOISE_FLOOR),
            Map.entry("firmness", MINIMUM_NOISE_FLOOR),
            Map.entry("acne", MINIMUM_NOISE_FLOOR),
            Map.entry("moisture", MINIMUM_NOISE_FLOOR),
            Map.entry("eye_bag", MINIMUM_NOISE_FLOOR),
            Map.entry("dark_circle_v2", MINIMUM_NOISE_FLOOR),
            Map.entry("age_spot", MINIMUM_NOISE_FLOOR),
            Map.entry("radiance", MINIMUM_NOISE_FLOOR),
            Map.entry("redness", MINIMUM_NOISE_FLOOR),
            Map.entry("oiliness", MINIMUM_NOISE_FLOOR),
            Map.entry("pore", MINIMUM_NOISE_FLOOR),
            Map.entry("texture", MINIMUM_NOISE_FLOOR),
            Map.entry("tear_trough", MINIMUM_NOISE_FLOOR));

    public VerdictAssessment assess(
            String concern,
            List<CaptureMeasurement> measurements,
            List<ConfoundFlag> confounds) {
        List<ConfoundFlag> safeConfounds = confounds == null ? List.of() : confounds.stream()
            .collect(java.util.stream.Collectors.toMap(
                ConfoundFlag::code,
                flag -> flag,
                (first, ignored) -> first,
                java.util.LinkedHashMap::new))
            .values().stream()
            .toList();
        Double minimumFloor = NOISE_FLOORS.get(concern);
        if (minimumFloor == null) {
            return inconclusive(concern, null, null, List.of(), List.of(), 0.0, 0, safeConfounds,
                    "The selected concern does not have a numeric score for trend analysis.");
        }

        Set<String> seenSamples = new HashSet<>();
        List<CaptureMeasurement> valid = measurements == null ? List.of() : measurements.stream()
                .filter(CaptureMeasurement::valid)
                .filter(measurement -> !measurement.simulated())
                .filter(measurement -> measurement.rawScore() != null
                        && Double.isFinite(measurement.rawScore())
                        && measurement.rawScore() >= 1.0
                        && measurement.rawScore() <= 100.0)
                .sorted(Comparator.comparing(CaptureMeasurement::takenAt))
                    .filter(measurement -> seenSamples.add(measurement.sampleKey()))
                .toList();
        List<CaptureMeasurement> baseline = valid.stream().filter(CaptureMeasurement::baseline).toList();
        List<CaptureMeasurement> followUps = valid.stream().filter(measurement -> !measurement.baseline()).toList();
        Double baselineMean = mean(baseline);
        Double currentMean = mean(latest(followUps, CURRENT_WINDOW_SIZE));
        Double delta = baselineMean == null || currentMean == null ? null : currentMean - baselineMean;
        long elapsedWeeks = elapsedWeeks(baseline, valid);
        boolean residualNoise = valid.size() >= RESIDUAL_UPDATE_CAPTURE_COUNT;
        double noiseFloor = residualNoise
                ? Math.max(minimumFloor, linearResidualRms(valid))
                : Math.max(minimumFloor, pairedBaselineNoise(baseline));

        String evidenceProblem = evidenceProblem(valid.size(), baseline.size(), followUps.size(), elapsedWeeks);
        if (evidenceProblem != null) {
            return inconclusive(concern, baselineMean, currentMean, baseline, valid, noiseFloor, elapsedWeeks,
                    safeConfounds, evidenceProblem, delta, residualNoise);
        }

        VerdictLabel label = classify(delta, noiseFloor, valid);
        ConfidenceLevel confidence = initialConfidence(label, delta, noiseFloor);
        long majorConfounds = safeConfounds.stream()
                .filter(flag -> flag.severity() == ConfoundSeverity.MAJOR)
                .count();
        for (int index = 0; index < safeConfounds.size(); index++) confidence = downgrade(confidence);
        if (majorConfounds >= 2) label = VerdictLabel.INCONCLUSIVE;

        return new VerdictAssessment(
                concern,
                label,
                confidence,
                baselineMean,
                currentMean,
                delta,
                noiseFloor,
                baseline.size(),
                valid.size(),
                followUps.size(),
                elapsedWeeks,
                residualNoise,
                explanation(concern, label, baselineMean, currentMean, delta, noiseFloor, safeConfounds),
                safeConfounds);
    }

    private static String evidenceProblem(int validCount, int baselineCount, int followUpCount, long elapsedWeeks) {
        if (validCount < MIN_VALID_CAPTURES) return "Not enough data yet: at least four valid captures are required.";
        if (baselineCount < MIN_BASELINE_CAPTURES) return "Not enough data yet: capture at least two valid baseline images.";
        if (followUpCount < MIN_FOLLOW_UP_CAPTURES) return "Not enough data yet: capture at least two valid follow-up images.";
        if (elapsedWeeks < MIN_ELAPSED_WEEKS) return "Not enough data yet: captures must span at least four weeks.";
        return null;
    }

    private static VerdictLabel classify(
            double delta,
            double noiseFloor,
            List<CaptureMeasurement> valid) {
        double magnitude = Math.abs(delta);
        if (magnitude < noiseFloor) return VerdictLabel.NO_DETECTABLE_CHANGE;
        boolean trendAgrees = lastThreeBeyondBaseline(valid, delta, noiseFloor)
                || Math.signum(linearSlope(valid)) == Math.signum(delta);
        if (magnitude >= IMPROVEMENT_MULTIPLIER * noiseFloor && trendAgrees) {
            return delta > 0 ? VerdictLabel.IMPROVED : VerdictLabel.WORSE;
        }
        return VerdictLabel.INCONCLUSIVE;
    }

    private static boolean lastThreeBeyondBaseline(
            List<CaptureMeasurement> valid,
            double delta,
            double noiseFloor) {
        if (valid.size() < 3) return false;
        double direction = Math.signum(delta);
        return valid.subList(valid.size() - 3, valid.size()).stream()
                .allMatch(measurement -> direction * (measurement.rawScore() - meanOfRaw(valid, true)) > noiseFloor);
    }

    private static double meanOfRaw(List<CaptureMeasurement> measurements, boolean baselineOnly) {
        return measurements.stream()
                .filter(measurement -> !baselineOnly || measurement.baseline())
                .map(CaptureMeasurement::rawScore)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private static ConfidenceLevel initialConfidence(VerdictLabel label, double delta, double noiseFloor) {
        if (label == VerdictLabel.IMPROVED || label == VerdictLabel.WORSE) {
            return Math.abs(delta) / noiseFloor >= 3.0 ? ConfidenceLevel.HIGH : ConfidenceLevel.MEDIUM;
        }
        if (label == VerdictLabel.NO_DETECTABLE_CHANGE) {
            return Math.abs(delta) / noiseFloor <= 0.5 ? ConfidenceLevel.HIGH : ConfidenceLevel.MEDIUM;
        }
        return ConfidenceLevel.LOW;
    }

    private static ConfidenceLevel downgrade(ConfidenceLevel confidence) {
        return switch (confidence) {
            case HIGH -> ConfidenceLevel.MEDIUM;
            case MEDIUM, LOW -> ConfidenceLevel.LOW;
        };
    }

    private static double pairedBaselineNoise(List<CaptureMeasurement> baseline) {
        if (baseline.size() < 2) return 0.0;
        List<Double> differences = new ArrayList<>();
        for (int left = 0; left < baseline.size(); left++) {
            for (int right = left + 1; right < baseline.size(); right++) {
                differences.add(Math.abs(baseline.get(right).rawScore() - baseline.get(left).rawScore()));
            }
        }
        return median(differences);
    }

    private static double linearResidualRms(List<CaptureMeasurement> measurements) {
        Regression regression = regression(measurements);
        Instant origin = measurements.getFirst().takenAt();
        double squaredResiduals = 0.0;
        for (CaptureMeasurement measurement : measurements) {
            double predicted = regression.intercept() + regression.slope() * elapsedDays(origin, measurement.takenAt());
            double residual = measurement.rawScore() - predicted;
            squaredResiduals += residual * residual;
        }
        return Math.sqrt(squaredResiduals / measurements.size());
    }

    private static double linearSlope(List<CaptureMeasurement> measurements) {
        return regression(measurements).slope();
    }

    private static Regression regression(List<CaptureMeasurement> measurements) {
        if (measurements.size() < 2) return new Regression(0.0, meanOfRaw(measurements, false));
        Instant origin = measurements.getFirst().takenAt();
        double meanX = measurements.stream()
                .mapToDouble(measurement -> elapsedDays(origin, measurement.takenAt()))
                .average()
                .orElse(0.0);
        double meanY = meanOfRaw(measurements, false);
        double numerator = 0.0;
        double denominator = 0.0;
        for (CaptureMeasurement measurement : measurements) {
            double x = elapsedDays(origin, measurement.takenAt());
            numerator += (x - meanX) * (measurement.rawScore() - meanY);
            denominator += (x - meanX) * (x - meanX);
        }
        if (denominator == 0.0) return new Regression(0.0, meanY);
        double slope = numerator / denominator;
        return new Regression(slope, meanY - slope * meanX);
    }

    private static double elapsedDays(Instant origin, Instant instant) {
        return Duration.between(origin, instant).toNanos() / 86_400_000_000_000.0;
    }

    private static long elapsedWeeks(List<CaptureMeasurement> baseline, List<CaptureMeasurement> valid) {
        if (baseline.isEmpty() || valid.isEmpty()) return 0;
        Instant firstBaseline = baseline.getFirst().takenAt();
        Instant latest = valid.getLast().takenAt();
        if (latest.isBefore(firstBaseline)) return 0;
        return ChronoUnit.DAYS.between(firstBaseline, latest) / 7;
    }

    private static List<CaptureMeasurement> latest(List<CaptureMeasurement> measurements, int count) {
        if (measurements.size() <= count) return measurements;
        return measurements.subList(measurements.size() - count, measurements.size());
    }

    private static Double mean(List<CaptureMeasurement> measurements) {
        return measurements.isEmpty()
                ? null
                : measurements.stream().map(CaptureMeasurement::rawScore).mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0
                : sorted.get(middle);
    }

    private static VerdictAssessment inconclusive(
            String concern,
            Double baselineMean,
            Double currentMean,
            List<CaptureMeasurement> baseline,
            List<CaptureMeasurement> valid,
            double noiseFloor,
            long elapsedWeeks,
            List<ConfoundFlag> confounds,
            String explanation) {
        Double delta = baselineMean == null || currentMean == null ? null : currentMean - baselineMean;
        return inconclusive(concern, baselineMean, currentMean, baseline, valid, noiseFloor, elapsedWeeks,
                confounds, explanation, delta, valid.size() >= RESIDUAL_UPDATE_CAPTURE_COUNT);
    }

    private static VerdictAssessment inconclusive(
            String concern,
            Double baselineMean,
            Double currentMean,
            List<CaptureMeasurement> baseline,
            List<CaptureMeasurement> valid,
            double noiseFloor,
            long elapsedWeeks,
            List<ConfoundFlag> confounds,
            String reason,
            Double delta,
            boolean residualNoise) {
        String explanation = reason;
        if (!confounds.isEmpty()) explanation += " Confounds noted: " + confoundCodes(confounds) + ".";
        return new VerdictAssessment(
                concern,
                VerdictLabel.INCONCLUSIVE,
                ConfidenceLevel.LOW,
                baselineMean,
                currentMean,
                delta,
                noiseFloor,
                baseline.size(),
                valid.size(),
                (int) valid.stream().filter(measurement -> !measurement.baseline()).count(),
                elapsedWeeks,
                residualNoise,
                explanation,
                confounds);
    }

    private static String explanation(
            String concern,
            VerdictLabel label,
            double baselineMean,
            double currentMean,
            double delta,
            double noiseFloor,
            List<ConfoundFlag> confounds) {
        String formattedDelta = String.format(Locale.ROOT, "%+.1f", delta);
        String text = switch (label) {
            case IMPROVED -> String.format(Locale.ROOT,
                    "The cosmetic %s score changed from baseline average %.1f to recent average %.1f (%s points), above the %.1f-point noise floor. The trend supports this direction. This is a cosmetic measurement, not a medical conclusion.",
                    concern, baselineMean, currentMean, formattedDelta, noiseFloor);
            case WORSE -> String.format(Locale.ROOT,
                    "The cosmetic %s score changed from baseline average %.1f to recent average %.1f (%s points), beyond the %.1f-point noise floor. The trend supports this direction. This is a cosmetic measurement, not a medical conclusion.",
                    concern, baselineMean, currentMean, formattedDelta, noiseFloor);
            case NO_DETECTABLE_CHANGE -> String.format(Locale.ROOT,
                    "The cosmetic %s score changed from baseline average %.1f to recent average %.1f (%s points), within the %.1f-point noise floor. No detectable change was measured.",
                    concern, baselineMean, currentMean, formattedDelta, noiseFloor);
            case INCONCLUSIVE -> String.format(Locale.ROOT,
                    "The cosmetic %s score changed by %s points, but the change and trend do not meet the verdict thresholds relative to the %.1f-point noise floor.",
                    concern, formattedDelta, noiseFloor);
        };
        if (!confounds.isEmpty()) text += " Confounds noted: " + confoundCodes(confounds) + "; confidence was reduced.";
        return text;
    }

    private static String confoundCodes(List<ConfoundFlag> confounds) {
        return confounds.stream().map(flag -> flag.code().name()).distinct().sorted()
                .reduce((left, right) -> left + ", " + right).orElse("");
    }

    private record Regression(double slope, double intercept) {
    }
}