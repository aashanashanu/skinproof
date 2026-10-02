package com.skinproof.api.ingredient;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record IngredientCheckRequest(@NotEmpty @Size(max = 40) List<@Size(max = 80) String> ingredients) {
}
