package com.prodbuddy.recipes;

import java.util.Collections;
import java.util.List;

public record RecipeDefinition(String name, String description, List<String> tags, boolean analysis, List<RecipeStep> steps) {

    public RecipeDefinition {
        tags = tags == null ? List.of() : Collections.unmodifiableList(tags);
        steps = steps == null ? List.of() : Collections.unmodifiableList(steps);
    }
}
