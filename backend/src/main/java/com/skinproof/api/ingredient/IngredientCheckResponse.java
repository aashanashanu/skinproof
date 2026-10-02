package com.skinproof.api.ingredient;

import java.util.List;

public record IngredientCheckResponse(
        List<IngredientMatch> matches,
        List<IngredientConflict> conflicts,
        String disclaimer) {
}
