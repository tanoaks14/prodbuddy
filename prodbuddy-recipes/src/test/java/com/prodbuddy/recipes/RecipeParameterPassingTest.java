package com.prodbuddy.recipes;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RecipeParameterPassingTest {

    @TempDir
    Path tempDir;

    @Test
    public void testParameterPassingToSubRecipe() throws Exception {
        // 1. Create a sub-recipe that uses a variable ${myArg}
        String subRecipeContent = "name: sub-recipe\n" +
                "## Step 1\n" +
                "tool: mock\n" +
                "operation: echo\n" +
                "value: ${myArg}";
        Files.writeString(tempDir.resolve("sub-recipe.md"), subRecipeContent);

        // 2. Create a parent recipe that calls the sub-recipe with a parameter
        String parentRecipeContent = "name: parent-recipe\n" +
                "## Step 1\n" +
                "tool: recipe\n" +
                "operation: run\n" +
                "name: sub-recipe\n" +
                "myArg: HelloParam";
        Files.writeString(tempDir.resolve("parent-recipe.md"), parentRecipeContent);

        // 3. Setup Executor
        java.util.concurrent.atomic.AtomicReference<String> receivedValue = new java.util.concurrent.atomic.AtomicReference<>();
        RecipeToolExecutor executor = (req, ctx) -> {
            if ("mock".equals(req.intent())) {
                String val = String.valueOf(req.payload().get("value"));
                receivedValue.set(val);
                return ToolResponse.ok(Map.of("received", val));
            }
            return ToolResponse.failure("UNKNOWN", "Unknown tool");
        };

        // 4. Run
        RecipeRegistry registry = RecipeRegistry.loadFrom(tempDir);
        RecipeDefinition parent = registry.findByName("parent-recipe");
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), 
                Map.of("RECIPES_DIR", tempDir.toString()), new ToolRegistry(Collections.emptyList()));
        
        RecipeRunner runner = new RecipeRunner();
        RecipeRunResult result = runner.run(parent, parentRecipeContent, context, executor);

        // 5. Verify
        assertEquals("HelloParam", receivedValue.get(), "Sub-recipe should receive the passed parameter via resolution");
    }
}
