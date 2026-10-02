package com.skinproof.api.ingredient;

public record IngredientMatch(
        String input,
        boolean recognized,
        String canonicalName,
        String irritationGroup,
        boolean sensitizing,
        String commonlyUsedFor) {
}
