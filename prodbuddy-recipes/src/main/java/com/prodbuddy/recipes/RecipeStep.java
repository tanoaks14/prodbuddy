package com.prodbuddy.recipes;

import java.util.Collections;
import java.util.Map;

public record RecipeStep(String name, String tool, String operation, Map<String, String> rawParams) {

    public RecipeStep {
        rawParams = rawParams == null ? Map.of() : Collections.unmodifiableMap(rawParams);
    }
}
