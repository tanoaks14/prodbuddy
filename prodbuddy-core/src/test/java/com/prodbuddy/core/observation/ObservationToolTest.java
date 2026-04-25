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
        assertTrue(mermaid.contains("User->>Interactive: ask (Ready?)"));
        assertTrue(mermaid.contains("Interactive->>NewRelic: list_apps (Search)"));
        assertTrue(mermaid.contains("NewRelic->>Observation: mermaid (Generate)"));
    }
}
