package com.skinproof.api.ingredient;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Loads the deterministic, data-driven starter ingredient rule set from a bundled resource file. */
@Component
public class IngredientRuleCatalog {
    private static final String RULES_RESOURCE = "/ingredient-rules.json";

    private final List<IngredientRule> rules;
    private final Map<String, IngredientRule> byAlias;

    public IngredientRuleCatalog() {
        this.rules = loadRules();
        this.byAlias = rules.stream()
                .flatMap(rule -> rule.aliases().stream().map(alias -> Map.entry(normalize(alias), rule)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, ignored) -> first));
    }

    public List<IngredientRule> rules() {
        return rules;
    }

    public java.util.Optional<IngredientRule> findByName(String rawName) {
        return java.util.Optional.ofNullable(byAlias.get(normalize(rawName)));
    }

    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static List<IngredientRule> loadRules() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream = IngredientRuleCatalog.class.getResourceAsStream(RULES_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled ingredient rule set: " + RULES_RESOURCE);
            }
            return mapper.readValue(stream, mapper.getTypeFactory().constructCollectionType(List.class, IngredientRule.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the ingredient rule set.", exception);
        }
    }
}
