package com.prodbuddy.recipes;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeVarResolverTest {

    private RecipeVarResolver resolver;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        resolver = new RecipeVarResolver();
        context = new ToolContext("test-req", Map.of("ENV_VAR", "env_val"), null);
    }

    @Test
    void testResolveSimpleString() {
        Object result = resolver.resolve("hello ${ENV_VAR}", context, Map.of());
        assertEquals("hello env_val", result);
    }

    @Test
    void testResolveComplexObjectPreservation() {
        Map<String, Object> complexData = Map.of("key", "value", "list", List.of(1, 2, 3));
        ToolResponse prevStep = ToolResponse.ok(Map.of("output", complexData));
        Map<String, ToolResponse> stepResults = Map.of("step1", prevStep);

        // Test resolving exactly one placeholder
        Object result = resolver.resolve("${step1.output}", context, stepResults);
        
        assertTrue(result instanceof Map, "Result should be a Map");
        assertEquals(complexData, result);
    }

    @Test
    void testResolveListPreservation() {
        List<String> listData = List.of("a", "b", "c");
        ToolResponse prevStep = ToolResponse.ok(Map.of("list", listData));
        Map<String, ToolResponse> stepResults = Map.of("step1", prevStep);

        Object result = resolver.resolve("${step1.list}", context, stepResults);
        
        assertTrue(result instanceof List, "Result should be a List");
        assertEquals(listData, result);
    }

    @Test
    void testResolveMixedContentForcedToString() {
        Map<String, Object> complexData = Map.of("key", "value");
        ToolResponse prevStep = ToolResponse.ok(Map.of("output", complexData));
        Map<String, ToolResponse> stepResults = Map.of("step1", prevStep);

        // If mixed with other text, it must become a string
        Object result = resolver.resolve("data: ${step1.output}", context, stepResults);
        
        assertTrue(result instanceof String, "Result should be a String");
        assertTrue(((String) result).contains("{key=value}"), "String should contain map representation");
    }

    @Test
    void testResolveAllRecursively() {
        Map<String, Object> complexData = Map.of("val", 42);
        ToolResponse prevStep = ToolResponse.ok(Map.of("data", complexData));
        Map<String, ToolResponse> stepResults = Map.of("step1", prevStep);

        Map<String, Object> rawParams = Map.of(
            "simple", "${ENV_VAR}",
            "complex", "${step1.data}",
            "nested", Map.of("inner", "${step1.data.val}")
        );

        Map<String, Object> resolved = resolver.resolveAll(rawParams, context, stepResults);

        assertEquals("env_val", resolved.get("simple"));
        assertEquals(complexData, resolved.get("complex"));
        assertEquals(42, ((Map)resolved.get("nested")).get("inner"));
    }

    @Test
    void testResolveDeeplyNestedVariables() {
        Map<String, Object> userData = Map.of("id", "u1", "roles", List.of("admin", "editor"));
        ToolResponse prevStep = ToolResponse.ok(Map.of("user", userData));
        Map<String, ToolResponse> stepResults = Map.of("auth", prevStep);

        Map<String, Object> variables = Map.of(
            "filter", Map.of(
                "userId", "${auth.user.id}",
                "activeOnly", true
            ),
            "tags", List.of("important", "${auth.user.roles[0]}")
        );

        Map<String, Object> rawParams = Map.of("variables", variables);
        Map<String, Object> resolvedParams = resolver.resolveAll(rawParams, context, stepResults);
        Map<String, Object> resolved = (Map<String, Object>) resolvedParams.get("variables");

        Map<String, Object> filter = (Map<String, Object>) resolved.get("filter");
        assertEquals("u1", filter.get("userId"));
        assertEquals(true, filter.get("activeOnly"));

        List<String> tags = (List<String>) resolved.get("tags");
        assertEquals("important", tags.get(0));
        assertEquals("admin", tags.get(1));
    }
}
