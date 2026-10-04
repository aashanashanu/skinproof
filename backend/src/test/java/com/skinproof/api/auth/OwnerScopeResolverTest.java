package com.skinproof.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class OwnerScopeResolverTest {
    @Test
    void authenticatedSubjectOverridesCallerSuppliedDemoWorkspace() {
        UUID userSubject = UUID.randomUUID();
        UUID attackerWorkspace = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .claim("sub", userSubject.toString())
                .build();

        OwnerScope resolved = OwnerScopeResolver.resolve(jwt, attackerWorkspace);

        assertEquals(OwnerScope.user(userSubject), resolved);
    }

    @Test
    void anonymousRequestsRequireAnExplicitDemoWorkspace() {
        UUID workspaceId = UUID.randomUUID();

        assertEquals(OwnerScope.demo(workspaceId), OwnerScopeResolver.resolve(null, workspaceId));
        assertThrows(ResponseStatusException.class, () -> OwnerScopeResolver.resolve(null, null));
    }

    @Test
    void invalidAuthenticatedSubjectCannotFallBackToCallerWorkspace() {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .claim("sub", "not-a-uuid")
                .build();

        assertThrows(ResponseStatusException.class, () -> OwnerScopeResolver.resolve(jwt, UUID.randomUUID()));
    }
}
