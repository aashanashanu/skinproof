package com.skinproof.api.auth;

import com.skinproof.api.demo.DemoWorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@Tag(name = "Account", description = "Cognito-authenticated account profile and owned-data controls")
public class AccountController {
    private final DemoWorkspaceService data;

    public AccountController(DemoWorkspaceService data) {
        this.data = data;
    }

    @GetMapping("/me")
    @Operation(summary = "Return the verified Cognito subject for the authenticated account")
    public AccountProfile me(@AuthenticationPrincipal Jwt jwt) {
        OwnerScope owner = requireUser(jwt);
        return new AccountProfile(owner.id());
    }

    @DeleteMapping("/data")
    @Operation(summary = "Permanently delete all experiments, captures, private images, and verdict data owned by this account")
    public ResponseEntity<Void> deleteOwnedData(@AuthenticationPrincipal Jwt jwt) {
        data.reset(requireUser(jwt));
        return ResponseEntity.noContent().build();
    }

    private static OwnerScope requireUser(Jwt jwt) {
        if (jwt == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED);
        try {
            return OwnerScope.user(UUID.fromString(jwt.getSubject()));
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
    }

    public record AccountProfile(UUID subject) {
    }
}
