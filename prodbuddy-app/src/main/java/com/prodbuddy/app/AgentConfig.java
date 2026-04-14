package com.prodbuddy.app;

import java.util.Map;

public record AgentConfig(
        boolean enabled,
        String provider,
        String baseUrl,
        String model,
        String chatPath,
        boolean authEnabled,
        String apiKey,
        boolean streamEnabled,
        boolean thinkingEnabled,
        boolean functionCallingEnabled,
        boolean functionCallingWithThinking
        ) {

    public static AgentConfig from(Map<String, String> environment) {
        return new AgentConfig(
                Boolean.parseBoolean(environment.getOrDefault("AGENT_ENABLED", "false")),
                environment.getOrDefault("AGENT_PROVIDER", "ollama"),
                environment.getOrDefault("AGENT_BASE_URL", "http://localhost:11434"),
                environment.getOrDefault("AGENT_MODEL", "gemma4:e4b"),
                environment.getOrDefault("AGENT_CHAT_PATH", "/api/generate"),
                Boolean.parseBoolean(environment.getOrDefault("AGENT_AUTH_ENABLED", "false")),
                environment.getOrDefault("AGENT_API_KEY", ""),
                Boolean.parseBoolean(environment.getOrDefault("AGENT_STREAM_ENABLED", "false")),
                Boolean.parseBoolean(environment.getOrDefault("AGENT_THINKING_ENABLED", "false")),
                Boolean.parseBoolean(environment.getOrDefault("AGENT_FUNCTION_CALLING_ENABLED", "false")),
                Boolean.parseBoolean(environment.getOrDefault("AGENT_FUNCTION_CALLING_WITH_THINKING", "false"))
        );
    }

    public String summary() {
        return provider + " model=" + model + " baseUrl=" + baseUrl + " enabled=" + enabled
                + " stream=" + streamEnabled + " thinking=" + thinkingEnabled
                + " functionCalling=" + functionCallingEnabled;
    }
}
