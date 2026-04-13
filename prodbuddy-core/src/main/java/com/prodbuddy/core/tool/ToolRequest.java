package com.prodbuddy.core.tool;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record ToolRequest(String intent, String operation, Map<String, Object> payload) {

    public ToolRequest   {
        Objects.requireNonNull(intent, "intent is required");
        Objects.requireNonNull(operation, "operation is required");
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(payload);
    }
}
