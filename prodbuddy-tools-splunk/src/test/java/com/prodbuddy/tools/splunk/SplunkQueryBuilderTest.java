package com.prodbuddy.tools.splunk;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class SplunkQueryBuilderTest {

    private SplunkQueryBuilder builder;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        context = new ToolContext("req-1", Map.of("SPLUNK_DEFAULT_SEARCH", "search *"), null);
        builder = new SplunkQueryBuilder();
    }

    @Test
    void testResolveSearchWithDirectSearch() {
        Map<String, Object> payload = Map.of("search", "index=main");
        ToolRequest request = new ToolRequest("splunk", "search", payload);
        
        String search = builder.resolveSearch(request, context);
        assertEquals("search index=main", search);
    }

    @Test
    void testResolveSearchWithQuery() {
        Map<String, Object> payload = Map.of("query", "index=test");
        ToolRequest request = new ToolRequest("splunk", "search", payload);
        
        String search = builder.resolveSearch(request, context);
        assertEquals("search index=test", search);
    }

    @Test
    void testResolveSearchWithComposedSearch() {
        Map<String, Object> payload = Map.of(
                "index", "logs",
                "host", "server1",
                "terms", "ERROR"
        );
        ToolRequest request = new ToolRequest("splunk", "search", payload);
        
        String search = builder.resolveSearch(request, context);
        assertEquals("search index=logs host=server1 ERROR", search);
    }

    @Test
    void testResolvePath() {
        assertEquals("/services/search/jobs/oneshot", builder.resolvePath("oneshot", Map.of()));
        assertEquals("/services/search/jobs/123/results", builder.resolvePath("results", Map.of("sid", "123")));
        assertEquals("/services/search/jobs", builder.resolvePath("search", Map.of()));
    }

    @Test
    void testBuildBody() {
        Map<String, Object> payload = Map.of(
                "earliestTime", "-1h",
                "count", 50
        );
        String body = builder.buildBody("search", payload, "search index=main");
        
        assertTrue(body.contains("search=search+index%3Dmain"));
        assertTrue(body.contains("earliest_time=-1h"));
        assertTrue(body.contains("count=50"));
        assertTrue(body.contains("output_mode=json"));
    }
}
