package com.prodbuddy.app;

import java.util.Map;

public record AgentConfig(
        boolean enabled,
        String provider,
        String baseUrl,
        String model,
        String chatPath,
        boolean authEnabled,
        String apiKey
        ) {

    public static AgentConfig from(Map<String, String> environment) {
        return new AgentConfig(
                Boolean.parseBoolean(environment.getOrDefault("AGENT_ENABLED", "false")),
                environment.getOrDefault("AGENT_PROVIDER", "ollama"),
                environment.getOrDefault("AGENT_BASE_URL", "http://localhost:11434"),
                environment.getOrDefault("AGENT_MODEL", "gemma4:e4b"),
                environment.getOrDefault("AGENT_CHAT_PATH", "/api/generate"),
                Boolean.parseBoolean(environment.getOrDefault("AGENT_AUTH_ENABLED", "false")),
                environment.getOrDefault("AGENT_API_KEY", "")
        );
    }

    public String summary() {
        return provider + " model=" + model + " baseUrl=" + baseUrl + " enabled=" + enabled;
    }
}
