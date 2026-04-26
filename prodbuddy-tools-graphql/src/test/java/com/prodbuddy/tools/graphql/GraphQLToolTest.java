package com.prodbuddy.tools.graphql;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphQLToolTest {

    private GraphQLTool tool;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        tool = new GraphQLTool();
        context = new ToolContext("req-1", Map.of(), null);
    }

    @Test
    void testExecuteMissingUrl() {
        ToolRequest request = new ToolRequest("graphql", "query", Map.of());
        ToolResponse response = tool.execute(request, context);
        assertEquals("MISSING_URL", response.errors().get(0).code());
    }

    @Test
    void testExecuteUnsupportedOp() {
        ToolRequest request = new ToolRequest("graphql", "unknown", Map.of("url", "http://test"));
        ToolResponse response = tool.execute(request, context);
        assertEquals("UNSUPPORTED_OP", response.errors().get(0).code());
    }

    @Test
    void testMetadata() {
        assertEquals("graphql", tool.metadata().name());
        assertTrue(tool.metadata().capabilities().contains("graphql.query"));
    }

    @Test
    void testSupports() {
        assertTrue(tool.supports(new ToolRequest("graphql", "query", Map.of())));
        assertTrue(tool.supports(new ToolRequest("GRAPHQL", "introspect", Map.of())));
    }
}
