package com.prodbuddy.core.tool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class ToolRegistryTest {

    @Test
    void shouldFindRegisteredTool() {
        Tool sample = new Tool() {
            @Override
            public ToolMetadata metadata() {
                return new ToolMetadata("sample", "sample tool", Set.of("test"));
            }

            @Override
            public boolean supports(ToolRequest request) {
                return true;
            }

            @Override
            public ToolResponse execute(ToolRequest request, ToolContext context) throws ToolExecutionException {
                return ToolResponse.ok(Map.of("ok", true));
            }
        };

        ToolRegistry registry = new ToolRegistry(List.of(sample));

        Assertions.assertTrue(registry.find("sample").isPresent());
    }
}
