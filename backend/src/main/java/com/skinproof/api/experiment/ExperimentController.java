package com.skinproof.api.experiment;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    public ExperimentController(ExperimentRepository experiments) {
        this.experiments = experiments;
    }

    @GetMapping
    public List<ExperimentResponse> list(@RequestHeader("X-Demo-Workspace") UUID workspaceId) {
        return experiments.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .stream()
                .map(ExperimentResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<ExperimentResponse> create(
            @RequestHeader("X-Demo-Workspace") UUID workspaceId,
            @Valid @RequestBody CreateExperimentRequest request) {
        ExperimentEntity created = experiments.save(new ExperimentEntity(workspaceId, request));
        return ResponseEntity.created(URI.create("/api/experiments/" + created.getId()))
            .body(ExperimentResponse.from(created));
    }

    @PostMapping("/{id}/archive")
    public ExperimentResponse archive(
            @RequestHeader("X-Demo-Workspace") UUID workspaceId,
            @PathVariable UUID id) {
        ExperimentEntity experiment = experiments.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        experiment.archive();
        return ExperimentResponse.from(experiments.save(experiment));
    }
}