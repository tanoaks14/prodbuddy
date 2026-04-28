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

import static org.junit.jupiter.api.Assertions.*;

public class GraphQLVariableTest {

    @TempDir
    Path tempDir;

    @Test
    public void testVariableTypes() throws Exception {
        String recipeContent = "name: variable-test\n" +
                "## Step 1\n" +
                "tool: mock\n" +
                "operation: test\n" +
                "isTrue: true\n" +
                "count: 123\n" +
                "text: hello\n" +
                "nested:\n" +
                "  active: false\n" +
                "  val: 456\n";
        Files.writeString(tempDir.resolve("variable-test.md"), recipeContent);

        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> capturedParams = new java.util.concurrent.atomic.AtomicReference<>();
        RecipeToolExecutor executor = (req, ctx) -> {
            capturedParams.set(req.payload());
            return ToolResponse.ok(Map.of());
        };

        RecipeRegistry registry = RecipeRegistry.loadFrom(tempDir);
        RecipeDefinition recipe = registry.findByName("variable-test");
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), Map.of(), new ToolRegistry(Collections.emptyList()));
        
        RecipeRunner runner = new RecipeRunner();
        runner.run(recipe, recipeContent, context, executor);

        Map<String, Object> params = capturedParams.get();
        
        // CURRENT BEHAVIOR (suspected): all are strings
        // assertTrue(params.get("isTrue") instanceof Boolean); // This will likely fail
        // assertTrue(params.get("count") instanceof Integer); // This will likely fail
        
        // NOW they are Boolean and Integer
        assertEquals(Boolean.TRUE, params.get("isTrue"));
        assertEquals(123, params.get("count"));

        Map<String, Object> nested = (Map<String, Object>) params.get("nested");
        assertEquals(Boolean.FALSE, nested.get("active"));
        assertEquals(456, nested.get("val"));
    }

    @Test
    public void testNestedLocalVariable() throws Exception {
        // Reproduce issue: nested property of loop variable not resolved
        String recipeContent = "name: loop-nested-test\n" +
                "## Step 1\n" +
                "tool: mock\n" +
                "operation: list\n" +
                "items: [{\"id\": 1, \"name\": \"first\"}, {\"id\": 2, \"name\": \"second\"}]\n" +
                "## Step 2\n" +
                "foreach: ${Step 1.items}\n" +
                "as: myItem\n" +
                "steps:\n" +
                "  - name: sub\n" +
                "    tool: mock\n" +
                "    operation: echo\n" +
                "    id: ${myItem.id}\n";
        
        Files.writeString(tempDir.resolve("loop-nested-test.md"), recipeContent);

        java.util.List<Object> capturedIds = new java.util.ArrayList<>();
        RecipeToolExecutor executor = (req, ctx) -> {
            if ("list".equals(req.operation())) {
                return ToolResponse.ok(Map.of("items", java.util.List.of(
                        Map.of("id", 1, "name", "first"),
                        Map.of("id", 2, "name", "second")
                )));
            }
            if ("echo".equals(req.operation())) {
                capturedIds.add(req.payload().get("id"));
                return ToolResponse.ok(Map.of());
            }
            return ToolResponse.failure("ERR", "Unknown op");
        };

        RecipeRegistry registry = RecipeRegistry.loadFrom(tempDir);
        RecipeDefinition recipe = registry.findByName("loop-nested-test");
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), Map.of("RECIPES_DIR", tempDir.toString()), new ToolRegistry(Collections.emptyList()));
        
        RecipeRunner runner = new RecipeRunner();
        runner.run(recipe, recipeContent, context, executor);

        assertEquals(2, capturedIds.size());
        assertEquals(1, capturedIds.get(0), "Should resolve nested property of local variable");
        assertEquals(2, capturedIds.get(1), "Should resolve nested property of local variable");
    }

    @Test
    public void testNestedVariableParsingIssue() throws Exception {
        // "if i use a nested variable not aat start then varraible above are not parsed"
        String recipeContent = "name: nested-test\n" +
                "## Step 1\n" +
                "tool: mock\n" +
                "operation: test\n" +
                "topVar: topValue\n" +
                "nested:\n" +
                "  innerVar: innerValue\n" +
                "bottomVar: bottomValue\n";
        Files.writeString(tempDir.resolve("nested-test.md"), recipeContent);

        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> capturedParams = new java.util.concurrent.atomic.AtomicReference<>();
        RecipeToolExecutor executor = (req, ctx) -> {
            capturedParams.set(req.payload());
            return ToolResponse.ok(Map.of());
        };

        RecipeRegistry registry = RecipeRegistry.loadFrom(tempDir);
        RecipeDefinition recipe = registry.findByName("nested-test");
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), Map.of(), new ToolRegistry(Collections.emptyList()));
        
        RecipeRunner runner = new RecipeRunner();
        runner.run(recipe, recipeContent, context, executor);

        Map<String, Object> params = capturedParams.get();
        
        assertNotNull(params.get("topVar"), "topVar should be present");
        assertEquals("topValue", params.get("topVar"));
        
        assertNotNull(params.get("nested"), "nested should be present");
        Map<String, Object> nested = (Map<String, Object>) params.get("nested");
        assertEquals("innerValue", nested.get("innerVar"));
        
        assertNotNull(params.get("bottomVar"), "bottomVar should be present");
        assertEquals("bottomValue", params.get("bottomVar"));
    }
}
