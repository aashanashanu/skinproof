package com.skinproof.api.ingredient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IngredientCheckServiceTest {
    private final IngredientCheckService service = new IngredientCheckService(new IngredientRuleCatalog());

    @Test
    void recognizesKnownActivesByAliasCaseInsensitively() {
        IngredientCheckResponse response = service.check(List.of(" Niacinamide ", "VITAMIN C"));

        assertEquals(2, response.matches().size());
        assertTrue(response.matches().get(0).recognized());
        assertEquals("Niacinamide", response.matches().get(0).canonicalName());
        assertEquals("Ascorbic Acid", response.matches().get(1).canonicalName());
    }

    @Test
    void unknownNameIsFlaggedWithoutGuessingAClaim() {
        IngredientCheckResponse response = service.check(List.of("made-up extract 9000"));

        assertFalse(response.matches().getFirst().recognized());
        assertEquals("made-up extract 9000", response.matches().getFirst().input());
        assertTrue(response.conflicts().isEmpty());
    }

    @Test
    void flagsOverlapWithinTheSameIrritationGroup() {
        IngredientCheckResponse response = service.check(List.of("Glycolic Acid", "Lactic Acid"));

        assertEquals(1, response.conflicts().size());
        assertEquals("GROUP_OVERLAP", response.conflicts().getFirst().type());
        assertTrue(response.conflicts().getFirst().ingredients().containsAll(
                List.of("Glycolic Acid", "Lactic Acid")));
    }

    @Test
    void flagsKnownCrossGroupInteraction() {
        IngredientCheckResponse response = service.check(List.of("Retinol", "Salicylic Acid"));

        assertTrue(response.conflicts().stream().anyMatch(conflict -> conflict.type().equals("KNOWN_INTERACTION")));
    }

    @Test
    void flagsSensitizingIngredientsEvenWithoutAConflictPartner() {
        IngredientCheckResponse response = service.check(List.of("Fragrance"));

        assertEquals(1, response.conflicts().size());
        assertEquals("SENSITIZING", response.conflicts().getFirst().type());
    }

    @Test
    void doesNotFlagUnrelatedIngredientsAsConflicting() {
        IngredientCheckResponse response = service.check(List.of("Niacinamide", "Hyaluronic Acid", "Squalane"));

        assertTrue(response.conflicts().isEmpty());
    }
}
