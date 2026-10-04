package com.skinproof.api.checkin;

import com.skinproof.api.auth.OwnerScopeResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiments/{experimentId}/check-ins")
@Tag(name = "Check-ins", description = "Workspace-scoped weekly check-ins and explainable confound suggestions")
public class CheckInController {
    private final CheckInService checkIns;

    public CheckInController(CheckInService checkIns) {
        this.checkIns = checkIns;
    }

    @GetMapping
    @Operation(summary = "List check-ins for an experiment")
    public List<CheckInResponse> list(
            @PathVariable UUID experimentId,
            @RequestHeader(name = "X-Demo-Workspace", required = false) UUID workspaceId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        return checkIns.list(experimentId, OwnerScopeResolver.resolve(jwt, workspaceId));
    }

    @PostMapping
    @Operation(summary = "Create or update a dated check-in and recompute the verdict")
    public CheckInResponse save(
            @PathVariable UUID experimentId,
            @RequestHeader(name = "X-Demo-Workspace", required = false) UUID workspaceId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CheckInRequest request) {
        return checkIns.save(experimentId, OwnerScopeResolver.resolve(jwt, workspaceId), request);
    }
}
