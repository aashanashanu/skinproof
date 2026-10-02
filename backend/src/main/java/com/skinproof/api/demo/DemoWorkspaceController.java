package com.skinproof.api.demo;

import com.skinproof.api.experiment.ExperimentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@Tag(name = "Demo workspace", description = "Reproducible, clearly-labeled synthetic demo data and workspace reset")
public class DemoWorkspaceController {
    private final DemoWorkspaceService demo;

    public DemoWorkspaceController(DemoWorkspaceService demo) {
        this.demo = demo;
    }

    @PostMapping("/seed")
    @Operation(summary = "Seed three labeled synthetic demo experiments into an empty workspace")
    public List<ExperimentResponse> seed(@RequestHeader("X-Demo-Workspace") UUID workspaceId) {
        return demo.seed(workspaceId);
    }

    @DeleteMapping("/data")
    @Operation(summary = "Permanently delete all experiments, captures, and private images for this workspace")
    public ResponseEntity<Void> reset(@RequestHeader("X-Demo-Workspace") UUID workspaceId) {
        demo.reset(workspaceId);
        return ResponseEntity.noContent().build();
    }
}
