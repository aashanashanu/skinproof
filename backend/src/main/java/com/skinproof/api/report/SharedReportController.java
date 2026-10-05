package com.skinproof.api.report;

import com.skinproof.api.auth.OwnerScopeResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Shared reports", description = "Immutable, revocable, expiring read-only report links")
public class SharedReportController {
    private final SharedReportService reports;

    public SharedReportController(SharedReportService reports) {
        this.reports = reports;
    }

    @PostMapping("/api/experiments/{experimentId}/reports")
    @Operation(summary = "Create a 30-day share link for an owned experiment")
    public CreateSharedReportResponse create(
            @PathVariable UUID experimentId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSharedReportRequest request) {
        return reports.create(experimentId, OwnerScopeResolver.resolve(jwt, null), request);
    }

    @GetMapping("/api/experiments/{experimentId}/reports")
    @Operation(summary = "List share-link status metadata for an owned experiment")
    public List<SharedReportRecordResponse> list(
            @PathVariable UUID experimentId,
            @AuthenticationPrincipal Jwt jwt) {
        return reports.list(experimentId, OwnerScopeResolver.resolve(jwt, null));
    }

    @DeleteMapping("/api/reports/{reportId}")
    @Operation(summary = "Revoke a share link owned by the signed-in account")
    public ResponseEntity<Void> revoke(@PathVariable UUID reportId, @AuthenticationPrincipal Jwt jwt) {
        reports.revoke(reportId, OwnerScopeResolver.resolve(jwt, null));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/shared-reports/{token}")
    @Operation(summary = "Read a public shared report by its high-entropy token")
    public ResponseEntity<SharedReportResponse> read(@PathVariable String token) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                    .body(reports.readPublic(token));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 404) throw exception;
            return ResponseEntity.notFound()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                    .build();
        }
    }
}