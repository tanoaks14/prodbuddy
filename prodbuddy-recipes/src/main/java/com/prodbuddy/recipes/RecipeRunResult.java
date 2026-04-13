package com.prodbuddy.recipes;

import java.util.Collections;
import java.util.List;

public record RecipeRunResult(String recipeName, List<RecipeStepResult> stepResults) {

    public RecipeRunResult {
        stepResults = stepResults == null ? List.of() : Collections.unmodifiableList(stepResults);
    }
}
