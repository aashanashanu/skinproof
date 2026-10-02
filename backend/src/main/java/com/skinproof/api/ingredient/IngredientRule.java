package com.skinproof.api.ingredient;

import java.util.List;

public record IngredientRule(
        String canonicalName,
        List<String> aliases,
        String irritationGroup,
        boolean sensitizing,
        String commonlyUsedFor) {
}
