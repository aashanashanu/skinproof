package com.skinproof.api.checkin;

import com.skinproof.api.capture.AnalysisStatus;
import com.skinproof.api.capture.CaptureEntity;
import com.skinproof.api.capture.CaptureRepository;
import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.OwnerType;
import com.skinproof.api.experiment.ExperimentEntity;
import com.skinproof.api.experiment.ExperimentRepository;
import com.skinproof.api.verdict.ConfoundCode;
import com.skinproof.api.verdict.ConfoundFlag;
import com.skinproof.api.verdict.VerdictResponse;
import com.skinproof.api.verdict.VerdictService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckInService {
    private static final Set<ConfoundCode> SELF_REPORTED_CODES = EnumSet.of(
            ConfoundCode.SUN_EXPOSURE,
            ConfoundCode.ILLNESS,
            ConfoundCode.TRAVEL,
            ConfoundCode.STRESS,
            ConfoundCode.NEW_PRODUCT,
            ConfoundCode.PROTOCOL_CHANGE);
    private static final Duration CAPTURE_GAP_THRESHOLD = Duration.ofDays(14);
    private static final int QUALITY_DRIFT_THRESHOLD = 5;
    private static final int RECENT_QUALITY_WINDOW = 3;

    private final ExperimentRepository experiments;
    private final CheckInRepository checkIns;
    private final CaptureRepository captures;
    private final VerdictService verdicts;

    public CheckInService(
            ExperimentRepository experiments,
            CheckInRepository checkIns,
            CaptureRepository captures,
            VerdictService verdicts) {
        this.experiments = experiments;
        this.checkIns = checkIns;
        this.captures = captures;
        this.verdicts = verdicts;
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> list(UUID experimentId, UUID workspaceId) {
        return list(experimentId, OwnerScope.demo(workspaceId));
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> list(UUID experimentId, OwnerScope owner) {
        requireExperiment(experimentId, owner);
        return checkIns.findAllByExperiment_IdOrderByCheckInDateAsc(experimentId).stream()
                .map(CheckInService::response)
                .toList();
    }

    @Transactional
    public CheckInResponse save(UUID experimentId, UUID workspaceId, CheckInRequest request) {
        return save(experimentId, OwnerScope.demo(workspaceId), request);
    }

    @Transactional
    public CheckInResponse save(UUID experimentId, OwnerScope owner, CheckInRequest request) {
        ExperimentEntity experiment = requireExperiment(experimentId, owner);
        List<CaptureEntity> experimentCaptures = captures.findAllByExperiment_IdOrderByTakenAtAsc(experimentId);
        if (!experimentCaptures.isEmpty() && experimentCaptures.stream().allMatch(CaptureEntity::isSimulated)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Check-ins are disabled for simulated demo experiments.");
        }
        if (request.checkInDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A check-in date cannot be in the future.");
        }

        List<ConfoundCode> reported = unique(request.reportedConfounds());
        if (!SELF_REPORTED_CODES.containsAll(reported)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Capture gaps and quality drift are detected from capture history, not self-reported.");
        }

        List<CheckInSuggestion> suggestions = detectSuggestions(experimentCaptures, request.checkInDate());
        Set<ConfoundCode> suggestedCodes = suggestions.stream()
                .map(CheckInSuggestion::code)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(ConfoundCode.class)));
        List<ConfoundCode> confirmed = unique(request.confirmedSuggestions());
        if (!suggestedCodes.containsAll(confirmed)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only suggestions shown for this check-in can be confirmed.");
        }

        CheckInEntity checkIn = checkIns.findByExperiment_IdAndCheckInDate(experimentId, request.checkInDate())
                .orElseGet(() -> new CheckInEntity(experiment, request.checkInDate(), List.of(), List.of(), List.of()));
        checkIn.update(reported, suggestions, confirmed);
        checkIns.save(checkIn);

        Set<ConfoundCode> currentConfounds = EnumSet.noneOf(ConfoundCode.class);
        for (CheckInEntity savedCheckIn : checkIns.findAllByExperiment_IdOrderByCheckInDateAsc(experimentId)) {
            currentConfounds.addAll(savedCheckIn.getReportedConfounds());
            currentConfounds.addAll(savedCheckIn.getConfirmedSuggestions());
        }
        List<ConfoundFlag> flags = currentConfounds.stream().map(ConfoundFlag::new).toList();
        VerdictResponse ignored = owner.type() == OwnerType.DEMO
            ? verdicts.recompute(experimentId, owner.id(), flags)
            : verdicts.recompute(experimentId, owner, flags);
        return response(checkIn);
    }

    private List<CheckInSuggestion> detectSuggestions(List<CaptureEntity> experimentCaptures, LocalDate checkInDate) {
        Instant checkInInstant = checkInDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<CaptureEntity> eligible = experimentCaptures.stream()
                .filter(CaptureEntity::isValid)
                .filter(capture -> !capture.isSimulated())
                .filter(capture -> capture.getAnalysis().getStatus() == AnalysisStatus.SUCCEEDED)
                .filter(capture -> !capture.getTakenAt().isAfter(checkInInstant))
                .sorted(Comparator.comparing(CaptureEntity::getTakenAt))
                .toList();

        List<CheckInSuggestion> suggestions = new ArrayList<>();
        eligible.stream().map(CaptureEntity::getTakenAt).max(Instant::compareTo).ifPresent(latest -> {
            Duration gap = Duration.between(latest, checkInInstant);
            if (gap.compareTo(CAPTURE_GAP_THRESHOLD) > 0) {
                suggestions.add(new CheckInSuggestion(
                        ConfoundCode.CAPTURE_GAP,
                        ConfoundCode.CAPTURE_GAP.severity(),
                        "The most recent valid analysis was more than 14 days before this check-in."));
            }
        });

        List<CaptureEntity> baseline = eligible.stream().filter(CaptureEntity::isBaseline).toList();
        List<CaptureEntity> followUps = eligible.stream().filter(capture -> !capture.isBaseline()).toList();
        if (!baseline.isEmpty() && followUps.size() >= 2) {
            double baselineAverage = baseline.stream().mapToInt(CaptureEntity::getQualityScore).average().orElse(0.0);
            List<CaptureEntity> recent = followUps.stream()
                    .skip(Math.max(0, followUps.size() - RECENT_QUALITY_WINDOW))
                    .toList();
            double recentAverage = recent.stream().mapToInt(CaptureEntity::getQualityScore).average().orElse(0.0);
            if (Math.abs(recentAverage - baselineAverage) >= QUALITY_DRIFT_THRESHOLD) {
                suggestions.add(new CheckInSuggestion(
                        ConfoundCode.QUALITY_DRIFT,
                        ConfoundCode.QUALITY_DRIFT.severity(),
                        "Recent capture-quality average differs from the baseline average by at least 5 points."));
            }
        }
        return List.copyOf(suggestions);
    }

    private ExperimentEntity requireExperiment(UUID experimentId, OwnerScope owner) {
        if (owner.type() == OwnerType.DEMO) {
            return experiments.findByIdAndWorkspaceId(experimentId, owner.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        return experiments.findByIdAndOwnerTypeAndOwnerId(experimentId, owner.type(), owner.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static List<ConfoundCode> unique(List<ConfoundCode> codes) {
        if (codes == null || codes.isEmpty()) return List.of();
        return List.copyOf(new LinkedHashSet<>(codes));
    }

    private static CheckInResponse response(CheckInEntity checkIn) {
        return new CheckInResponse(
                checkIn.getId(),
                checkIn.getCheckInDate(),
                checkIn.getReportedConfounds(),
                checkIn.getAutomaticSuggestions(),
                checkIn.getConfirmedSuggestions(),
                false);
    }
}
