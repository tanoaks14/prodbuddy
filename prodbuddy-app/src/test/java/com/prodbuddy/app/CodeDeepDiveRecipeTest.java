package com.prodbuddy.app;

import com.prodbuddy.context.ContextCollector;
import com.prodbuddy.context.ContextFormatter;
import com.prodbuddy.context.ConversationContext;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.orchestrator.AgentLoopOrchestrator;
import com.prodbuddy.orchestrator.LoopConfig;
import com.prodbuddy.orchestrator.RuleBasedToolRouter;
import com.prodbuddy.recipes.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CodeDeepDiveRecipeTest {

    @Test
    public void runAsRecipe() throws Exception {
        Path projectPath = Paths.get("d:/apps/prodbuddy").toAbsolutePath();
        Path dbPath = projectPath.resolve("target/codegraph_recipe_run");
        Path recipeDir = projectPath.resolve("recipes");
        String recipeName = "code-deep-dive";

        ToolBootstrap bootstrap = new ToolBootstrap();
        ToolRegistry registry = bootstrap.createRegistry();

        AgentLoopOrchestrator orchestrator = new AgentLoopOrchestrator(
                registry, new RuleBasedToolRouter(), LoopConfig.defaults()
        );

        RecipeRegistry recipeRegistry = RecipeRegistry.loadFrom(recipeDir);
        RecipeDefinition recipe = recipeRegistry.findByName(recipeName);

        Map<String, String> env = new LinkedHashMap<>();
        env.put("projectPath", projectPath.toString());
        env.put("dbPath", dbPath.toString());
        env.put("searchTerm", "SELECT queries are allowed");
        env.put("RECIPES_DIR", recipeDir.toString());

        ToolContext context = new ToolContext(UUID.randomUUID().toString(), env, registry);
        ConversationContext convCtx = new ConversationContext(context.requestId());
        ContextCollector collector = new ContextCollector(orchestrator::run, convCtx);

        String fullContent = Files.readString(recipeDir.resolve(recipeName + ".md"));
        RecipeRunner runner = new RecipeRunner();
        RecipeRunResult result = runner.run(recipe, fullContent, context, collector);

        String contextData = ContextFormatter.format(convCtx);
        Path contextFile = projectPath.resolve("code-deep-dive-context.md");
        Files.writeString(contextFile, contextData);

        System.out.println("Recipe execution complete.");
        System.out.println("Context saved to: " + contextFile);
    }
}
