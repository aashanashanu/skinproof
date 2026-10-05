package com.skinproof.api.reminder;

import com.skinproof.api.auth.OwnerScopeResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/reminder-preferences")
@Tag(name = "Reminder preferences", description = "Explicit account-owned reminder settings; delivery is not enabled")
public class ReminderPreferenceController {
    private final ReminderPreferenceService preferences;

    public ReminderPreferenceController(ReminderPreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    @Operation(summary = "Read reminder preferences for the authenticated account")
    public ReminderPreferenceResponse get(@AuthenticationPrincipal Jwt jwt) {
        return preferences.get(userId(jwt));
    }

    @PutMapping
    @Operation(summary = "Set or unsubscribe from reminder preferences for the authenticated account")
    public ReminderPreferenceResponse save(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReminderPreferenceRequest request) {
        return preferences.save(userId(jwt), request);
    }

    private static UUID userId(Jwt jwt) {
        return OwnerScopeResolver.resolve(jwt, null).id();
    }
}