package com.skinproof.api.capture;

import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.OwnerType;
import com.skinproof.api.verdict.VerdictService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaptureBaselineService {
    private final CaptureRepository captures;
    private final VerdictService verdicts;

    public CaptureBaselineService(CaptureRepository captures, VerdictService verdicts) {
        this.captures = captures;
        this.verdicts = verdicts;
    }

    @Transactional
    public CaptureResponse markAsBaseline(UUID captureId, OwnerScope owner) {
        CaptureEntity capture = (owner.type() == OwnerType.DEMO
                ? captures.findByIdAndWorkspaceId(captureId, owner.id())
                : captures.findByIdAndOwnerTypeAndOwnerId(captureId, owner.type(), owner.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!capture.isValid() || capture.getAnalysis().getStatus() != AnalysisStatus.SUCCEEDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only valid, successfully analyzed captures can be made baseline.");
        }
        if (!capture.isBaseline()) {
            capture.markAsBaseline();
            captures.saveAndFlush(capture);
            verdicts.recomputeWithExistingConfounds(capture.getExperiment().getId(), owner);
        }
        return CaptureResponse.from(capture);
    }
}