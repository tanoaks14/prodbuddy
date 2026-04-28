package com.prodbuddy.recipes;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class VariableRobustnessTest {

    @TempDir
    Path tempDir;

    @Test
    public void testComprehensiveVariableResolution() throws Exception {
        String recipeContent = "name: robust-test\n" +
                "## Step1\n" +
                "tool: mock\n" +
                "operation: data\n" +
                "jsonBody: '{\"user\": {\"id\": 101, \"roles\": [\"admin\", \"user\"], \"meta\": {\"lastLogin\": \"2024-01-01\"}}}'\n" +
                "simpleInt: 42\n" +
                "simpleBool: true\n" +
                "\n" +
                "## Step2\n" +
                "tool: mock\n" +
                "operation: verify\n" +
                "params:\n" +
                "  # 1. Basic typed resolution\n" +
                "  id: ${Step1.simpleInt}\n" +
                "  active: ${Step1.simpleBool}\n" +
                "  \n" +
                "  # 2. Deep path with array index\n" +
                "  firstRole: ${Step1.jsonBody.user.roles[0]}\n" +
                "  \n" +
                "  # 3. Multiple placeholders in one string\n" +
                "  greeting: \"User ${Step1.jsonBody.user.id} has role ${Step1.jsonBody.user.roles[1]}\"\n" +
                "  \n" +
                "  # 4. Smart diving into JSON string\n" +
                "  loginDate: ${Step1.jsonBody.user.meta.lastLogin}\n" +
                "  \n" +
                "  # 5. Environment variables and defaults\n" +
                "  project: ${PRODBUDDY_PROJECT_PATH}\n" +
                "  missing: ${NON_EXISTENT_VAR}\n" +
                "  \n" +
                "  # 6. Quoted vs Unquoted types\n" +
                "  quotedTrue: \"true\"\n" +
                "  unquotedTrue: true\n" +
                "  quotedNumber: \"123\"\n" +
                "  unquotedNumber: 123\n" +
                "\n" +
                "## Step3\n" +
                "foreach: ${Step1.jsonBody.user.roles}\n" +
                "as: role\n" +
                "steps:\n" +
                "  - name: sub\n" +
                "    tool: mock\n" +
                "    operation: echo\n" +
                "    val: \"Role: ${role}\"\n";

        Files.writeString(tempDir.resolve("robust-test.md"), recipeContent);

        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> capturedParams = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.List<Object> capturedSubVals = new java.util.ArrayList<>();

        RecipeToolExecutor executor = (req, ctx) -> {
            if ("data".equals(req.operation())) {
                return ToolResponse.ok(Map.of(
                        "jsonBody", "{\"user\": {\"id\": 101, \"roles\": [\"admin\", \"user\"], \"meta\": {\"lastLogin\": \"2024-01-01\"}}}",
                        "simpleInt", 42,
                        "simpleBool", true
                ));
            }
            if ("verify".equals(req.operation())) {
                capturedParams.set(req.payload());
                return ToolResponse.ok(Map.of());
            }
            if ("echo".equals(req.operation()) || (req.payload().containsKey("val"))) {
                capturedSubVals.add(req.payload().get("val"));
                return ToolResponse.ok(Map.of());
            }
            return ToolResponse.ok(Map.of());
        };

        RecipeRegistry registry = RecipeRegistry.loadFrom(tempDir);
        RecipeDefinition recipe = registry.findByName("robust-test");
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), 
                Map.of("RECIPES_DIR", tempDir.toString()), 
                new ToolRegistry(Collections.emptyList()));
        
        RecipeRunner runner = new RecipeRunner();
        runner.run(recipe, recipeContent, context, executor);

        assertNotNull(capturedParams.get(), "Step 2 should have been executed and captured params");
        Map<String, Object> p = (Map<String, Object>) capturedParams.get().get("params");
        assertNotNull(p, "params Map should be present in Step 2 payload");

        // 1. Basic typed resolution
        assertEquals(42, p.get("id"));
        assertEquals(Boolean.TRUE, p.get("active"));

        // 2. Deep path with array index
        assertEquals("admin", p.get("firstRole"));

        // 3. Multiple placeholders
        assertEquals("User 101 has role user", p.get("greeting"));

        // 4. Smart diving
        assertEquals("2024-01-01", p.get("loginDate"));

        // 5. Env & Defaults
        assertEquals(".", p.get("project"));
        assertEquals("${NON_EXISTENT_VAR}", p.get("missing"));

        // 6. Quoted vs Unquoted
        assertEquals("true", p.get("quotedTrue"));
        assertEquals(Boolean.TRUE, p.get("unquotedTrue"));
        assertEquals("123", p.get("quotedNumber"));
        assertEquals(123, p.get("unquotedNumber"));

        // 7. Loop variables
        assertEquals(2, capturedSubVals.size());
        assertEquals("Role: admin", capturedSubVals.get(0));
        assertEquals("Role: user", capturedSubVals.get(1));
    }

    @Test
    public void testDeepNestedLocalVariable() throws Exception {
        // Edge case: deeply nested local variable properties
        String recipeContent = "name: deep-local-test\n" +
                "## Step 1\n" +
                "tool: mock\n" +
                "operation: list\n" +
                "## Step 2\n" +
                "foreach: ${Step 1.items}\n" +
                "as: item\n" +
                "steps:\n" +
                "  - name: sub\n" +
                "    id: ${item.data.id}\n" +
                "    tag: ${item.tags[1]}\n";
        
        Files.writeString(tempDir.resolve("deep-local-test.md"), recipeContent);

        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> lastSubParams = new java.util.concurrent.atomic.AtomicReference<>();
        RecipeToolExecutor executor = (req, ctx) -> {
            if ("list".equals(req.operation())) {
                return ToolResponse.ok(Map.of("items", List.of(
                        Map.of("data", Map.of("id", "ABC"), "tags", List.of("t1", "t2"))
                )));
            }
            lastSubParams.set(req.payload());
            return ToolResponse.ok(Map.of());
        };

        RecipeRegistry registry = RecipeRegistry.loadFrom(tempDir);
        RecipeDefinition recipe = registry.findByName("deep-local-test");
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), Map.of(), new ToolRegistry(Collections.emptyList()));
        
        RecipeRunner runner = new RecipeRunner();
        runner.run(recipe, recipeContent, context, executor);

        Map<String, Object> p = lastSubParams.get();
        assertEquals("ABC", p.get("id"));
        assertEquals("t2", p.get("tag"));
    }
    
    @Test
    public void testEdgeCases() throws Exception {
        RecipeVarResolver resolver = new RecipeVarResolver();
        ToolContext ctx = new ToolContext("cid", Map.of("MY_VAR", "val"), new ToolRegistry(List.of()));
        Map<String, ToolResponse> results = Map.of(
            "Step1", ToolResponse.ok(Map.of("nullVal", Collections.singletonMap("k", null), "emptyStr", ""))
        );

        // Null value in map
        assertEquals("${Step1.nullVal.k}", resolver.resolve("${Step1.nullVal.k}", ctx, results));
        
        // Empty path - returns the node itself currently
        assertTrue(resolver.resolve("${Step1.}", ctx, results) instanceof Map);

        // Mixed types in string
        results = Map.of("S", ToolResponse.ok(Map.of("i", 10, "b", true)));
        assertEquals("Count: 10, Status: true", resolver.resolve("Count: ${S.i}, Status: ${S.b}", ctx, results));
        
        // Non-existent step
        assertEquals("${Ghost.data}", resolver.resolve("${Ghost.data}", ctx, results));
    }

    @Test
    public void testFileContentStrippingIssue() throws Exception {
        Path gqlFile = tempDir.resolve("query.graphql");
        // Leading newline and spaces are common in GQL files
        String gqlContent = "\n  query Test {\n    user { id }\n  }\n";
        Files.writeString(gqlFile, gqlContent);

        String recipeContent = "name: stripping-test\n" +
                "## Step 1\n" +
                "tool: graphql\n" +
                "query: @file:query.graphql\n";
        Files.writeString(tempDir.resolve("stripping-test.md"), recipeContent);

        RecipeRegistry registry = RecipeRegistry.loadFrom(tempDir);
        RecipeDefinition recipe = registry.findByName("stripping-test");
        
        RecipeStep step = recipe.steps().get(0);
        String query = (String) step.rawParams().get("query");
        
        // readFileContent does NOT trim content in the current implementation
        assertEquals(gqlContent, query);
    }

    @Test
    public void testBlockFileStrippingIssue() throws Exception {
        Path gqlFile = tempDir.resolve("query2.graphql");
        String gqlContent = "query {\n  user {\n    id\n  }\n}";
        Files.writeString(gqlFile, gqlContent);

        String recipeContent = "name: block-test\n" +
                "## Step 1\n" +
                "tool: graphql\n" +
                "query: |\n" +
                "  @file:query2.graphql\n";
        Files.writeString(tempDir.resolve("block-test.md"), recipeContent);

        RecipeRegistry registry = RecipeRegistry.loadFrom(tempDir);
        RecipeDefinition recipe = registry.findByName("block-test");
        
        RecipeStep step = recipe.steps().get(0);
        String query = (String) step.rawParams().get("query");
        
        assertEquals(gqlContent + "\n", query);
    }

    @Test
    public void testImplicitBlockStrippingIssue() throws Exception {
        String recipeContent = "name: implicit-test\n" +
                "## Step 1\n" +
                "tool: graphql\n" +
                "query:\n" +
                "  query Test {\n" +
                "    user { id }\n" +
                "  }\n";
        Files.writeString(tempDir.resolve("implicit-test.md"), recipeContent);

        RecipeRegistry registry = RecipeRegistry.loadFrom(tempDir);
        RecipeDefinition recipe = registry.findByName("implicit-test");
        
        RecipeStep step = recipe.steps().get(0);
        String query = (String) step.rawParams().get("query");
        
        // Expected: preserved indentation relative to the block (2 spaces removed from each line)
        assertTrue(query.contains("  user"), "Indentation should be preserved (2 spaces relative), but got: " + query);
    }

    @Test
    public void testOverIndentedFileIssue() throws Exception {
        Path gqlFile = tempDir.resolve("query3.graphql");
        String gqlContent = "query { user { id } }";
        Files.writeString(gqlFile, gqlContent);

        String recipeContent = "name: over-indented-test\n" +
                "## Step 1\n" +
                "tool: graphql\n" +
                "query: |\n" +
                "    @file:query3.graphql\n"; // 4 spaces
        Files.writeString(tempDir.resolve("over-indented-test.md"), recipeContent);

        RecipeRegistry registry = RecipeRegistry.loadFrom(tempDir);
        RecipeDefinition recipe = registry.findByName("over-indented-test");
        
        RecipeStep step = recipe.steps().get(0);
        String query = (String) step.rawParams().get("query");
        
        assertTrue(query.contains("query { user"), "File content should be loaded, but got: " + query);
    }

    @Test
    void testListOfMapsVariable() throws Exception {
        String recipeYaml = """
                name: list-map-test
                
                ## Step 1
                tool: test
                operation: test
                config:
                  - feature: 1
                    version: "v3"
                  - feature: 2
                    version: "v4"
                """;
        Path recipeFile = tempDir.resolve("list-map-test.md");
        Files.writeString(recipeFile, recipeYaml);
        RecipeDefinition recipe = new RecipeLoader().load(recipeFile);
        RecipeStep step = recipe.steps().get(0);
        Object config = step.rawParams().get("config");
        
        assertTrue(config instanceof List, "Config should be a list, but was: " + (config == null ? "null" : config.getClass().getName()));
        List<?> list = (List<?>) config;
        assertEquals(2, list.size());
        
        Map<?, ?> item1 = (Map<?, ?>) list.get(0);
        assertEquals(1, item1.get("feature"));
        assertEquals("v3", item1.get("version")); 
        
        Map<?, ?> item2 = (Map<?, ?>) list.get(1);
        assertEquals(2, item2.get("feature"));
        assertEquals("v4", item2.get("version"));
    }

    @Test
    void testInlineJsonVariable() throws Exception {
        String recipeYaml = """
                name: json-var-test
                
                ## Step 1
                tool: test
                config: [{"f": 1, "v": "a"}, {"f": 2, "v": "b"}]
                """;
        Path recipeFile = tempDir.resolve("json-var-test.md");
        Files.writeString(recipeFile, recipeYaml);

        RecipeDefinition recipe = new RecipeLoader().load(recipeFile);
        RecipeStep step = recipe.steps().get(0);
        Object config = step.rawParams().get("config");
        
        assertTrue(config instanceof List, "Config should be a parsed List");
        List<?> list = (List<?>) config;
        assertEquals(2, list.size());
        assertEquals("a", ((Map<?, ?>) list.get(0)).get("v"));
    }
}
