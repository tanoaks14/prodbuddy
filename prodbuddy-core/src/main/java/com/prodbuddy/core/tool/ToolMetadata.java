package com.prodbuddy.core.tool;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public record ToolMetadata(String name, String description, Set<String> capabilities) {

    public ToolMetadata   {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(description, "description is required");
        capabilities = capabilities == null ? Set.of() : Collections.unmodifiableSet(capabilities);
    }
}
