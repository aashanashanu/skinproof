package com.skinproof.api.ingredient;

import java.util.List;

public record IngredientConflict(String type, List<String> ingredients, String note) {
}
