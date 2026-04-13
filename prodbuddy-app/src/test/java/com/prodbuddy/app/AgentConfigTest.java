package com.prodbuddy.app;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AgentConfigTest {

    @Test
    void shouldLoadOllamaDefaults() {
        AgentConfig config = AgentConfig.from(Map.of());

        Assertions.assertEquals("ollama", config.provider());
        Assertions.assertEquals("http://localhost:11434", config.baseUrl());
        Assertions.assertEquals("gemma4:e4b", config.model());
        Assertions.assertFalse(config.enabled());
    }
}
