package com.prodbuddy.recipes;

import java.util.Collections;
import java.util.Map;

import com.prodbuddy.core.tool.ToolResponse;

public record RecipeStepResult(
        String stepName,
        String tool,
        String operation,
        Map<String, String> resolvedParams,
        ToolResponse response
        ) {

    public RecipeStepResult     {
        resolvedParams = resolvedParams == null ? Map.of() : Collections.unmodifiableMap(resolvedParams);
    }
}
