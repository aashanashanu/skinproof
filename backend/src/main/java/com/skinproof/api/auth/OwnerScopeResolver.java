package com.skinproof.api.auth;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

public class OwnerScopeResolver {
    private OwnerScopeResolver() {
    }

    public static OwnerScope resolve(Jwt jwt, UUID demoWorkspaceId) {
        if (jwt != null) {
            String subject = jwt.getSubject();
            try {
                return OwnerScope.user(UUID.fromString(subject));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(UNAUTHORIZED, "The authenticated subject is invalid.");
            }
        }
        if (demoWorkspaceId != null) return OwnerScope.demo(demoWorkspaceId);
        throw new ResponseStatusException(UNAUTHORIZED, "Sign in or provide a demo workspace.");
    }
}
