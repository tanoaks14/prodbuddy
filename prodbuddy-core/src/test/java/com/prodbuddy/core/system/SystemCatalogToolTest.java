package com.prodbuddy.core.system;

import com.prodbuddy.core.tool.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class SystemCatalogToolTest {

    @Test
    void shouldListTools() {
        Tool sample = new Tool() {
            @Override
            public ToolMetadata metadata() {
                return new ToolMetadata("sample", "sample", Set.of("a"));
            }

            @Override
            public boolean supports(ToolRequest request) {
                return true;
            }

            @Override
            public ToolResponse execute(ToolRequest request, ToolContext context) {
                return ToolResponse.ok(Map.of());
            }
        };

        ToolRegistry registry = new ToolRegistry(List.of(sample));
        ToolRouter router = request -> Optional.of("sample");
        SystemCatalogTool tool = new SystemCatalogTool(() -> registry, router);

        ToolContext context = new ToolContext("req-1", Map.of("ENV", "test"), registry);
        ToolResponse response = tool.execute(new ToolRequest("system", "list_tools", Map.of()), context);
        Assertions.assertTrue(response.success());
    }
}
