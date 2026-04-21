package com.prodbuddy.recipes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record RecipeStep(
        String name,
        String tool,
        String operation,
        String condition,
        String foreach,
        String as,
        boolean stopOnFailure,
        List<RecipeStep> nestedSteps,
        Map<String, Object> rawParams
) {

    public RecipeStep {
        rawParams = rawParams == null ? Map.of() : Collections.unmodifiableMap(rawParams);
        nestedSteps = nestedSteps == null ? List.of() : Collections.unmodifiableList(nestedSteps);
        foreach = foreach == null ? "" : foreach;
        as = as == null ? "" : as;
    }

    /**
     * Minimal constructor for standard steps.
     */
    public RecipeStep(String name, String tool, String operation, String condition, Map<String, Object> params) {
        this(name, tool, operation, condition, "", "", false, List.of(), params);
    }
}
