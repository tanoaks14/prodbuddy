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

class SplunkToolDeepTest {

    private SplunkTool tool;
    private SplunkOperationGuard guard;
    private ToolContext context;
    
    @Mock
    private HttpClient httpClient;
    
    @Mock
    private HttpResponse<String> httpResponse;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        guard = new SplunkOperationGuard();
        tool = new SplunkTool(guard, httpClient);
        context = new ToolContext("req-1", Map.of(
                "SPLUNK_BASE_URL", "https://localhost:8089",
                "SPLUNK_TOKEN", "env-token"
        ), null);
        
        when(httpClient.send(any(java.net.http.HttpRequest.class), any(java.net.http.HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }

    @Test
    void testAuthPriorityPayloadOverEnv() {
        ToolRequest request = new ToolRequest("splunk", "search", Map.of(
                "token", "payload-token"
        ));
        String auth = tool.resolveValidAuthHeaderOrThrow(request, context, "https://localhost:8089");
        assertEquals("Splunk payload-token", auth);
    }

    @Test
    void testAuthPriorityEnvFallback() {
        ToolRequest request = new ToolRequest("splunk", "search", Map.of());
        String auth = tool.resolveValidAuthHeaderOrThrow(request, context, "https://localhost:8089");
        assertEquals("Splunk env-token", auth);
    }

    @Test
    void testTruncationLogic() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        String longBody = "A".repeat(100);
        when(httpResponse.body()).thenReturn(longBody);
        
        ToolRequest request = new ToolRequest("splunk", "search", Map.of(
                "maxOutputChars", 10
        ));
        
        ToolResponse response = tool.execute(request, context);
        assertTrue(response.success());
        assertEquals(10, ((String)response.data().get("body")).length());
        assertEquals(true, response.data().get("truncated"));
    }

    @Test
    void testNoTruncateFlag() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        String longBody = "A".repeat(100);
        when(httpResponse.body()).thenReturn(longBody);
        
        ToolRequest request = new ToolRequest("splunk", "search", Map.of(
                "noTruncate", "true",
                "maxOutputChars", 10
        ));
        
        ToolResponse response = tool.execute(request, context);
        assertEquals(100, ((String)response.data().get("body")).length());
        assertEquals(false, response.data().get("truncated"));
    }

    @Test
    void testLoginFlow() throws Exception {
        // Mock login response
        when(httpResponse.body()).thenReturn("{\"sessionKey\":\"secret-key\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        
        ToolRequest request = new ToolRequest("splunk", "login", Map.of(
                "username", "admin",
                "password", "changeme"
        ));
        
        ToolResponse response = tool.execute(request, context);
        assertTrue(response.success());
        assertEquals("secret-key", response.data().get("sessionKey"));
        assertEquals("splunkd_8089=secret-key", response.data().get("cookie"));
    }

    @Test
    void testBrowserMimicJobCreation() throws Exception {
        // Mock SID response from custom v2/jobs path
        when(httpResponse.body()).thenReturn("{\"sid\":\"12345.abc\"}");
        when(httpResponse.statusCode()).thenReturn(201);
        
        ToolRequest request = new ToolRequest("splunk", "search", Map.of(
                "path", "en-gb/splunkd/__raw/servicesNS/admin/search/v2/jobs",
                "search", "index=_internal | head 5"
        ));
        
        ToolResponse response = tool.execute(request, context);
        assertTrue(response.success());
        assertEquals("12345.abc", response.data().get("sid"));
        assertEquals("search", response.data().get("op"));
    }
}
