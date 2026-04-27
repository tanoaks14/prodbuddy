package com.prodbuddy.app;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.recipes.RecipeDefinition;
import com.prodbuddy.recipes.RecipeRegistry;
import com.prodbuddy.recipes.RecipeRunResult;
import com.prodbuddy.recipes.RecipeRunner;
import com.prodbuddy.recipes.RecipeStepResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GraphQLComplexRecipeTest {

    @Test
    public void testGraphQLComplexQuery() throws Exception {
        Path projectPath = Paths.get("d:/apps/prodbuddy").toAbsolutePath();
        Path recipeDir = projectPath.resolve("recipes");
        String recipeName = "graphql-complex-test";

        ToolBootstrap bootstrap = new ToolBootstrap();
        ToolRegistry registry = bootstrap.createRegistry();

        RecipeRegistry recipeRegistry = RecipeRegistry.loadFrom(recipeDir);
        RecipeDefinition recipe = recipeRegistry.findByName(recipeName);

        Map<String, String> env = new LinkedHashMap<>();
        env.put("RECIPES_DIR", recipeDir.toString());
        env.put("AGENT_ENABLED", "true");

        ToolContext context = new ToolContext(UUID.randomUUID().toString(), env, registry);
        
        // We need a dummy executor for the agent step if we don't want to call LLM, 
        // but since we want to know if it works, let's just use the real one or a simple one.
        // For this test, we just want to see if Step 1 succeeds.
        
        String fullContent = Files.readString(recipeDir.resolve(recipeName + ".md"));
        RecipeRunner runner = new RecipeRunner();
        RecipeRunResult result = runner.run(recipe, fullContent, context, (req, ctx) -> {
            return registry.find(req.intent())
                    .orElseThrow(() -> new RuntimeException("Tool not found: " + req.intent()))
                    .execute(req, ctx);
        });

        System.out.println("Recipe execution complete.");
        for (RecipeStepResult stepRes : result.stepResults()) {
            System.out.println("Step: " + stepRes.stepName() + " Success: " + stepRes.response().success());
            if (!stepRes.response().success()) {
                System.out.println("Error: " + stepRes.response().errors());
            }
        }

        assertTrue(result.stepResults().get(0).response().success(), "Step 1 should succeed");
        com.fasterxml.jackson.databind.JsonNode data = (com.fasterxml.jackson.databind.JsonNode) 
                result.stepResults().get(0).response().data().get("data");
        assertTrue(data != null && !data.isEmpty(), "Data should not be empty");
        System.out.println("GQL Data: " + data);
    }
}
