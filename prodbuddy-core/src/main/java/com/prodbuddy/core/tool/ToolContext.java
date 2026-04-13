package com.prodbuddy.core.tool;

import java.util.Collections;
import java.util.Map;

public record ToolContext(String requestId, Map<String, String> environment) {

    public ToolContext {
        environment = environment == null ? Map.of() : Collections.unmodifiableMap(environment);
    }

    public String env(String key) {
        return environment.get(key);
    }

    public String envOrDefault(String key, String defaultValue) {
        return environment.getOrDefault(key, defaultValue);
    }
}
