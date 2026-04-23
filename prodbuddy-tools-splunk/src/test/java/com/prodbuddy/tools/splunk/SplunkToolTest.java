package com.prodbuddy.tools.splunk;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SplunkToolTest {

    private SplunkTool tool;
    private SplunkOperationGuard guard;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        guard = new SplunkOperationGuard();
        tool = new SplunkTool(guard);
        context = new ToolContext("req-1", Map.of("SPLUNK_BASE_URL", "https://localhost:8089"), null);
    }

    @Test
    void testExecuteForbiddenOperation() {
        // Forbidden operation test would need a mock or a specific guard setup
        // But the current guard allows everything we use.
        // I'll skip the forbidden test for now or keep it if I can mock it.
        // Actually, I'll just test a random string that's not allowed.
        ToolRequest request = new ToolRequest("splunk", "forbidden_op", Map.of());
        
        ToolResponse response = tool.execute(request, context);
        assertEquals("SPLUNK_FORBIDDEN", response.errors().get(0).code());
    }

    @Test
    void testExecuteMissingBaseUrl() {
        ToolContext emptyCtx = new ToolContext("req-2", Map.of(), null);
        ToolRequest request = new ToolRequest("splunk", "search", Map.of());
        
        ToolResponse response = tool.execute(request, emptyCtx);
        assertEquals("SPLUNK_CONFIG", response.errors().get(0).code());
    }

    @Test
    void testMetadata() {
        assertEquals("splunk", tool.metadata().name());
        assertTrue(tool.metadata().capabilities().contains("splunk.search"));
    }

    @Test
    void testSupports() {
        assertTrue(tool.supports(new ToolRequest("splunk", "search", Map.of())));
        assertTrue(tool.supports(new ToolRequest("SPLUNK", "oneshot", Map.of())));
    }
}
