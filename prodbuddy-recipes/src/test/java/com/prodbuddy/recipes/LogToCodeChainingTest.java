package com.prodbuddy.recipes;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogToCodeChainingTest {

    @TempDir
    Path tempDir;

    @Test
    public void testLogToCodeChaining() throws Exception {
        // 1. Create the orchestrator recipe
        String masterRecipe = "name: master\n" +
                "## Step 1: Logs\n" +
                "tool: splunk\n" +
                "operation: oneshot\n" +
                "search: status=500\n" +
                "\n" +
                "## Step 2: Extract\n" +
                "tool: agent\n" +
                "operation: think\n" +
                "prompt: extract errors\n" +
                "\n" +
                "## Step 3: Loop\n" +
                "foreach: ${Step 2.result.errors}\n" +
                "as: sig\n" +
                "steps:\n" +
                "  - name: Dive\n" +
                "    tool: mock\n" +
                "    operation: analyze\n" +
                "    term: ${sig}";
        Files.writeString(tempDir.resolve("master.md"), masterRecipe);

        // 2. Mock responses
        AtomicInteger diveCount = new AtomicInteger(0);
        List<String> receivedTerms = new ArrayList<>();

        RecipeToolExecutor executor = (req, ctx) -> {
            if ("splunk".equals(req.intent())) {
                return ToolResponse.ok(Map.of("result", "Raw log data with errors"));
            }
            if ("agent".equals(req.intent())) {
                // Return a JSON string as the result
                return ToolResponse.ok(Map.of("result", "{\"errors\": [\"ErrorA\", \"ErrorB\"]}"));
            }
            if ("mock".equals(req.intent())) {
                diveCount.incrementAndGet();
                receivedTerms.add(String.valueOf(req.payload().get("term")));
                return ToolResponse.ok(Map.of("status", "analyzed"));
            }
            return ToolResponse.failure("FAIL", "Unknown");
        };

        // 3. Run
        RecipeRegistry registry = RecipeRegistry.loadFrom(tempDir);
        RecipeDefinition recipe = registry.findByName("master");
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), 
                Map.of("RECIPES_DIR", tempDir.toString()), new ToolRegistry(Collections.emptyList()));
        
        RecipeRunner runner = new RecipeRunner();
        runner.run(recipe, masterRecipe, context, executor);

        // 4. Verify
        // Should have called 'mock.analyze' twice
        assertEquals(2, diveCount.get(), "Should have triggered 2 investigations");
        assertEquals(List.of("ErrorA", "ErrorB"), receivedTerms, "Should have passed correct signatures");
    }
}
