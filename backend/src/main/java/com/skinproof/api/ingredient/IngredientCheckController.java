package com.skinproof.api.ingredient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingredient-check")
@Tag(name = "Ingredient check", description = "Deterministic starter ingredient reference; not medical advice")
public class IngredientCheckController {
    private final IngredientCheckService service;

    public IngredientCheckController(IngredientCheckService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Match a product's ingredient list against the starter active-ingredient rule set")
    public IngredientCheckResponse check(@Valid @RequestBody IngredientCheckRequest request) {
        return service.check(request.ingredients());
    }
}
