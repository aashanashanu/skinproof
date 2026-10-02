package com.skinproof.api.ingredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Deterministic, data-driven ingredient matching; makes no medical or efficacy claims. */
@Service
public class IngredientCheckService {
    private static final String DISCLAIMER =
            "This is a cautious starter reference, not medical advice. It does not evaluate your full "
            + "product formula, concentration, or personal sensitivities.";

    // Fixed, deterministic pairs of irritation groups commonly flagged for combined use.
    private static final List<String[]> KNOWN_GROUP_INTERACTIONS = List.of(
            new String[] {"RETINOID", "AHA"},
            new String[] {"RETINOID", "BHA"},
            new String[] {"RETINOID", "BENZOYL_PEROXIDE"},
            new String[] {"BENZOYL_PEROXIDE", "VITAMIN_C"});

    private final IngredientRuleCatalog catalog;

    public IngredientCheckService(IngredientRuleCatalog catalog) {
        this.catalog = catalog;
    }

    public IngredientCheckResponse check(List<String> rawIngredientNames) {
        List<IngredientMatch> matches = rawIngredientNames.stream()
                .map(this::match)
                .toList();
        List<IngredientConflict> conflicts = new ArrayList<>();
        conflicts.addAll(groupOverlapConflicts(matches));
        conflicts.addAll(knownInteractionConflicts(matches));
        conflicts.addAll(sensitizingNotes(matches));
        return new IngredientCheckResponse(matches, conflicts, DISCLAIMER);
    }

    private IngredientMatch match(String rawName) {
        return catalog.findByName(rawName)
                .map(rule -> new IngredientMatch(
                        rawName, true, rule.canonicalName(), rule.irritationGroup(), rule.sensitizing(), rule.commonlyUsedFor()))
                .orElseGet(() -> new IngredientMatch(
                        rawName, false, null, null, false,
                        "Not in SkinProof's starter ingredient list; no claims available for this name."));
    }

    private static List<IngredientConflict> groupOverlapConflicts(List<IngredientMatch> matches) {
        Map<String, Set<String>> byGroup = new LinkedHashMap<>();
        for (IngredientMatch match : matches) {
            if (!match.recognized() || match.irritationGroup() == null) continue;
            byGroup.computeIfAbsent(match.irritationGroup(), ignored -> new LinkedHashSet<>()).add(match.canonicalName());
        }
        List<IngredientConflict> conflicts = new ArrayList<>();
        byGroup.forEach((group, ingredients) -> {
            if (ingredients.size() > 1) {
                conflicts.add(new IngredientConflict(
                        "GROUP_OVERLAP",
                        List.copyOf(ingredients),
                        "Combining multiple " + group.replace('_', ' ').toLowerCase(java.util.Locale.ROOT)
                                + " actives in one routine may increase irritation risk; consider alternating nights."));
            }
        });
        return conflicts;
    }

    private static List<IngredientConflict> knownInteractionConflicts(List<IngredientMatch> matches) {
        Map<String, Set<String>> byGroup = new LinkedHashMap<>();
        for (IngredientMatch match : matches) {
            if (!match.recognized() || match.irritationGroup() == null) continue;
            byGroup.computeIfAbsent(match.irritationGroup(), ignored -> new LinkedHashSet<>()).add(match.canonicalName());
        }
        List<IngredientConflict> conflicts = new ArrayList<>();
        for (String[] pair : KNOWN_GROUP_INTERACTIONS) {
            Set<String> left = byGroup.get(pair[0]);
            Set<String> right = byGroup.get(pair[1]);
            if (left == null || right == null) continue;
            Set<String> ingredients = new LinkedHashSet<>();
            ingredients.addAll(left);
            ingredients.addAll(right);
            conflicts.add(new IngredientConflict(
                    "KNOWN_INTERACTION",
                    List.copyOf(ingredients),
                    "Combining " + pair[0].replace('_', ' ').toLowerCase(java.util.Locale.ROOT) + " and "
                            + pair[1].replace('_', ' ').toLowerCase(java.util.Locale.ROOT)
                            + " actives is commonly cited as an irritation risk; introduce one at a time."));
        }
        return conflicts;
    }

    private static List<IngredientConflict> sensitizingNotes(List<IngredientMatch> matches) {
        List<String> sensitizing = matches.stream()
                .filter(match -> match.recognized() && match.sensitizing())
                .map(IngredientMatch::canonicalName)
                .distinct()
                .toList();
        if (sensitizing.isEmpty()) return List.of();
        return List.of(new IngredientConflict(
                "SENSITIZING",
                sensitizing,
                "These are commonly reported as sensitizing for some people; consider a patch test."));
    }
}
