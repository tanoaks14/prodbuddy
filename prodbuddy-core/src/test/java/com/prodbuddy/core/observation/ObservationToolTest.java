package com.prodbuddy.core.observation;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.observation.RecordingSequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObservationToolTest {

    @Test
    void testMermaidGeneration() {
        RecordingSequenceLogger logger = new RecordingSequenceLogger(
                new Slf4jSequenceLogger(ObservationToolTest.class));
        ObservationTool tool = new ObservationTool(logger);

        // Record some events
        logger.logSequence("User", "Interactive", "ask", "Ready?");
        logger.logSequence("Interactive", "NewRelic", "list_apps", "Search");
        logger.logSequence("NewRelic", "Observation", "mermaid", "Generate");

        ToolRequest request = new ToolRequest("observation", "mermaid", Map.of());
        ToolRegistry registry = new ToolRegistry(List.of(tool));
        ToolContext context = new ToolContext("test-id", Map.of(), registry);
        
        ToolResponse response = tool.execute(request, context);

        assertTrue(response.success());
        String mermaid = (String) response.data().get("mermaid");
        
        System.out.println("Generated Mermaid:\n" + mermaid);
        
        assertTrue(mermaid.contains("sequenceDiagram"));
        // Check for participant declarations
        assertTrue(mermaid.contains("participant \"User\" as actor_User"));
        assertTrue(mermaid.contains("participant \"Interactive\" as actor_Interactive"));
        
        // Check for arrows using safe IDs
        assertTrue(mermaid.contains("actor_User->>actor_Interactive: ask (Ready?)"));
    }

    @Test
    void testMermaidGenerationWithSpecialCharactersAndTruncation() {
        RecordingSequenceLogger logger = new RecordingSequenceLogger(
                new Slf4jSequenceLogger(ObservationToolTest.class));
        ObservationTool tool = new ObservationTool(logger);

        // 1. Test special characters that might break Mermaid
        logger.logSequence("Service A", "Database (Prod)", "find[User]", "ID: #123; name: 'O'Reilly'");
        
        // 2. Test truncation (new limit is 200, showing first 200)
        for (int i = 0; i < 210; i++) {
            logger.logSequence("Source", "Target", "step", "iteration " + i);
        }

        ToolRequest request = new ToolRequest("observation", "mermaid", Map.of());
        ToolRegistry registry = new ToolRegistry(List.of(tool));
        ToolContext context = new ToolContext("test-id", Map.of(), registry);
        
        ToolResponse response = tool.execute(request, context);

        assertTrue(response.success());
        String mermaid = (String) response.data().get("mermaid");
        
        System.out.println("Generated Mermaid (Truncated):\n" + mermaid);
        
        String[] lines = mermaid.split("\n");
        long stepLines = java.util.Arrays.stream(lines).filter(l -> l.contains("->>")).count();
        assertEquals(200, stepLines, "Should be limited to 200 events");
        
        // Check participant declarations with spaces and special chars
        assertTrue(mermaid.contains("participant \"Service A\" as actor_Service_A"));
        assertTrue(mermaid.contains("participant \"Database (Prod)\" as actor_Database__Prod_"));
        
        // Check if characters like [] and # are preserved in labels
        assertTrue(mermaid.contains("actor_Service_A->>actor_Database__Prod_: find[User] (ID: #123; name: 'O'Reilly')"));
        
        // Check truncation note
        assertTrue(mermaid.contains("Note over actor_Service_A, actor_Target: ... trace truncated after 200 steps ..."));
    }
}
