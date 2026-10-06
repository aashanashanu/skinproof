package com.skinproof.api.experiment;

import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.OwnerScopeResolver;
import com.skinproof.api.auth.OwnerType;
import com.skinproof.api.demo.DemoWorkspaceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {
    private final ExperimentRepository experiments;
    private final DemoWorkspaceService experimentData;

    public ExperimentController(ExperimentRepository experiments, DemoWorkspaceService experimentData) {
        this.experiments = experiments;
        this.experimentData = experimentData;
    }

    @GetMapping
    public List<ExperimentResponse> list(
            @RequestHeader(name = "X-Demo-Workspace", required = false) UUID workspaceId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        OwnerScope owner = OwnerScopeResolver.resolve(jwt, workspaceId);
        return experiments.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(owner.type(), owner.id())
                .stream()
                .map(ExperimentResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<ExperimentResponse> create(
            @RequestHeader(name = "X-Demo-Workspace", required = false) UUID workspaceId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateExperimentRequest request) {
        OwnerScope owner = OwnerScopeResolver.resolve(jwt, workspaceId);
        ExperimentEntity created = experiments.save(new ExperimentEntity(owner, request));
        return ResponseEntity.created(URI.create("/api/experiments/" + created.getId()))
            .body(ExperimentResponse.from(created));
    }

    @PostMapping("/{id}/archive")
    public ExperimentResponse archive(
            @RequestHeader(name = "X-Demo-Workspace", required = false) UUID workspaceId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        OwnerScope owner = OwnerScopeResolver.resolve(jwt, workspaceId);
        ExperimentEntity experiment = experiments.findByIdAndOwnerTypeAndOwnerId(id, owner.type(), owner.id())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        experiment.archive();
        return ExperimentResponse.from(experiments.save(experiment));
    }

    @PostMapping("/{id}/restore")
    public ExperimentResponse restore(
            @RequestHeader(name = "X-Demo-Workspace", required = false) UUID workspaceId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        OwnerScope owner = OwnerScopeResolver.resolve(jwt, workspaceId);
        ExperimentEntity experiment = experiments.findByIdAndOwnerTypeAndOwnerId(id, owner.type(), owner.id())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        experiment.restore();
        return ExperimentResponse.from(experiments.save(experiment));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(name = "X-Demo-Workspace", required = false) UUID workspaceId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        OwnerScope owner = OwnerScopeResolver.resolve(jwt, workspaceId);
        experimentData.deleteExperiment(id, owner);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/claim-demo-data")
    public ClaimDemoDataResponse claimDemoData(
            @RequestHeader("X-Demo-Workspace") UUID workspaceId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        OwnerScope owner = OwnerScopeResolver.resolve(jwt, null);
        if (owner.type() != OwnerType.USER) throw new ResponseStatusException(NOT_FOUND);
        int claimed = experiments.claimDemoWorkspace(workspaceId, owner.id());
        return new ClaimDemoDataResponse(claimed);
    }

    public record ClaimDemoDataResponse(int claimedExperiments) {
    }
}