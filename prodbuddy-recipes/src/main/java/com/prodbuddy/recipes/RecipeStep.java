package com.prodbuddy.recipes;

import java.util.Collections;
import java.util.Map;

public record RecipeStep(String name, String tool, String operation, String condition, Map<String, Object> rawParams) {

    public RecipeStep {
        rawParams = rawParams == null ? Map.of() : Collections.unmodifiableMap(rawParams);
    }
}
