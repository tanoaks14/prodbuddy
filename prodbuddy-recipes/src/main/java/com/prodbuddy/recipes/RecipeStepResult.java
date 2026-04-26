package com.prodbuddy.recipes;

import com.prodbuddy.core.tool.ToolResponse;

import java.util.Collections;
import java.util.Map;

public record RecipeStepResult(
        String stepName,
        String tool,
        String operation,
        Map<String, Object> resolvedParams,
        ToolResponse response
        ) {

    public RecipeStepResult     {
        resolvedParams = resolvedParams == null ? Map.of() : Collections.unmodifiableMap(resolvedParams);
    }
}
