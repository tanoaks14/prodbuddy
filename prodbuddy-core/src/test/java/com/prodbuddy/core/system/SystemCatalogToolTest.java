package com.prodbuddy.core.system;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.core.tool.ToolRouter;

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

        ToolResponse response = tool.execute(new ToolRequest("system", "list_tools", Map.of()), new ToolContext("1", Map.of()));
        Assertions.assertTrue(response.success());
    }
}
