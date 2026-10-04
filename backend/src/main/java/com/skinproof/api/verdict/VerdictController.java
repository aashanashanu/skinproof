package com.skinproof.api.verdict;

import com.skinproof.api.auth.OwnerScopeResolver;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/experiments/{experimentId}/verdict")
public class VerdictController {
    private final VerdictService verdicts;

    public VerdictController(VerdictService verdicts) {
        this.verdicts = verdicts;
    }

    @GetMapping
    public VerdictResponse get(
            @PathVariable UUID experimentId,
            @RequestHeader(name = "X-Demo-Workspace", required = false) UUID workspaceId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        return verdicts.getLatest(experimentId, OwnerScopeResolver.resolve(jwt, workspaceId));
    }

    @PostMapping
    public VerdictResponse recompute(
            @PathVariable UUID experimentId,
            @RequestHeader(name = "X-Demo-Workspace", required = false) UUID workspaceId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) VerdictRecomputeRequest request) {
        List<ConfoundFlag> confounds = request == null ? List.of() : request.confounds().stream()
            .map(ConfoundFlag::new)
            .toList();
        try {
            return verdicts.recompute(experimentId, OwnerScopeResolver.resolve(jwt, workspaceId), confounds);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}