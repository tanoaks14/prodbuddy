package com.prodbuddy.tools.http;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class GenericApiToolTest {

    private GenericApiTool tool;
    private HttpMethodSupport support;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        support = new HttpMethodSupport();
        tool = new GenericApiTool(support);
        context = new ToolContext("req-1", Map.of(), null);
    }

    @Test
    void testExecuteMissingUrl() {
        ToolRequest request = new ToolRequest("http", "get", Map.of());
        ToolResponse response = tool.execute(request, context);
        assertEquals("HTTP_URL", response.errors().get(0).code());
    }

    @Test
    void testExecuteUnsupportedMethod() {
        ToolRequest request = new ToolRequest("http", "CONNECT", Map.of("url", "http://test"));
        ToolResponse response = tool.execute(request, context);
        assertEquals("HTTP_METHOD", response.errors().get(0).code());
    }

    @Test
    void testMetadata() {
        assertEquals("http", tool.metadata().name());
        assertTrue(tool.metadata().capabilities().contains("http.get"));
    }

    @Test
    void testSupports() {
        assertTrue(tool.supports(new ToolRequest("http", "get", Map.of())));
        assertTrue(tool.supports(new ToolRequest("api", "post", Map.of())));
    }
}
