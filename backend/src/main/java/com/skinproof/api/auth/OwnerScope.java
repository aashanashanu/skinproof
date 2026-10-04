package com.skinproof.api.auth;

import java.util.UUID;

public record OwnerScope(OwnerType type, UUID id) {
    public OwnerScope {
        if (type == null || id == null) throw new IllegalArgumentException("Owner type and ID are required.");
    }

    public static OwnerScope demo(UUID workspaceId) {
        return new OwnerScope(OwnerType.DEMO, workspaceId);
    }

    public static OwnerScope user(UUID cognitoSubject) {
        return new OwnerScope(OwnerType.USER, cognitoSubject);
    }
}
