package com.prodbuddy.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolError;
import com.prodbuddy.orchestrator.AgentLoopOrchestrator;
import com.prodbuddy.recipes.RecipeDefinition;
import com.prodbuddy.recipes.RecipeRegistry;
import com.prodbuddy.recipes.RecipeReport;
import com.prodbuddy.recipes.RecipeRunResult;
import com.prodbuddy.recipes.RecipeRunner;
import com.prodbuddy.recipes.RecipeStepResult;

/**
 * Handles --run-recipe, --list-recipes, and --add-recipe CLI modes. Dispatches
 * to the appropriate handler based on args[0].
 */
final class RecipeCliHandler {

    private RecipeCliHandler() {
    }

    static void handle(
            String[] args,
            AgentLoopOrchestrator orchestrator,
            Map<String, String> environment,
            AgentConfig agentConfig
    ) {
        String mode = args[0].toLowerCase();
        switch (mode) {
            case "--list-recipes" ->
                handleList(environment);
            case "--run-recipe" ->
                handleRun(args, orchestrator, environment, agentConfig);
            case "--add-recipe" ->
                new RecipeAddWizard(new LinkedHashMap<>(environment)).run();
            default ->
                System.out.println("Unknown recipe command: " + mode);
        }
    }

    private static void handleList(Map<String, String> environment) {
        String dir = environment.getOrDefault("RECIPES_DIR", "recipes");
        RecipeRegistry registry = RecipeRegistry.loadFrom(Path.of(dir));
        List<RecipeDefinition> all = registry.all();
        if (all.isEmpty()) {
            System.out.println("No recipes found in: " + dir);
            return;
        }
        System.out.println("Available recipes (" + dir + "):");
        for (RecipeDefinition recipe : all) {
            System.out.printf("  %-35s %s  tags=%s%n",
                    recipe.name(), recipe.description(), recipe.tags());
        }
    }

    private static void handleRun(
            String[] args,
            AgentLoopOrchestrator orchestrator,
            Map<String, String> environment,
            AgentConfig agentConfig
    ) {
        if (args.length < 2) {
            System.out.println("Usage: --run-recipe <name> [--vars KEY=VALUE ...]");
            return;
        }
        String name = args[1];
        Map<String, String> env = applyVars(args, environment);
        String dir = env.getOrDefault("RECIPES_DIR", "recipes");
        RecipeDefinition recipe = RecipeRegistry.loadFrom(Path.of(dir)).findByName(name);
        if (recipe == null) {
            System.out.println("Recipe not found: " + name + " (dir=" + dir + ")");
            return;
        }
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), env);
        RecipeRunResult result = new RecipeRunner().run(recipe, context, orchestrator::run);
        printRecipeSteps(result);
        Map<String, Object> summary = RecipeReport.summarize(result);
        printRecipeSummary(summary);
        runRecipeLlm(name, summary, agentConfig);
    }

    private static Map<String, String> applyVars(String[] args, Map<String, String> environment) {
        Map<String, String> result = new LinkedHashMap<>(environment);
        boolean inVars = false;
        for (String arg : args) {
            if ("--vars".equalsIgnoreCase(arg)) {
                inVars = true;
                continue;
            }
            if (inVars && arg.contains("=")) {
                int eq = arg.indexOf('=');
                result.put(arg.substring(0, eq), arg.substring(eq + 1));
            } else {
                inVars = false;
            }
        }
        return result;
    }

    private static void runRecipeLlm(String name, Map<String, Object> summary, AgentConfig config) {
        if (!config.enabled() || !"ollama".equalsIgnoreCase(config.provider())) {
            return;
        }
        String summaryText = String.valueOf(summary).replace('"', '\'');
        String prompt = "Recipe: " + name + "\\nResult: " + summaryText
                + "\\nIdentify failures, likely root causes, and recommended next steps.";
        String response = new OllamaAgentClient().generate(prompt, config);
        System.out.println("Recipe assistant:");
        System.out.println(TerminalMarkdownRenderer.toTerminalText(response));
    }

    private static void printRecipeSteps(RecipeRunResult result) {
        System.out.println();
        System.out.println("Recipe execution:");
        for (RecipeStepResult step : result.stepResults()) {
            String call = step.tool() + "." + step.operation();
            System.out.println("- " + step.stepName() + " (call: " + call + ")");
            String status = step.response().success() ? "ok" : "failed";
            String summary = step.response().success()
                    ? summarizeSuccess(step.response().data())
                    : summarizeErrors(step.response().errors());
            System.out.println("  result: " + status + " - " + summary);
        }
    }

    @SuppressWarnings("unchecked")
    private static void printRecipeSummary(Map<String, Object> summary) {
        System.out.println();
        System.out.println("Recipe summary:");
        System.out.println("- Recipe: " + summary.getOrDefault("recipe", ""));
        System.out.println("- Total steps: " + summary.getOrDefault("totalSteps", 0));
        System.out.println("- Passed: " + summary.getOrDefault("passed", 0));
        System.out.println("- Failed: " + summary.getOrDefault("failed", 0));

        List<Map<String, Object>> failedSteps = (List<Map<String, Object>>) summary.getOrDefault("failedSteps", List.of());
        if (!failedSteps.isEmpty()) {
            System.out.println("- Failed steps:");
            for (Map<String, Object> failure : failedSteps) {
                String call = String.valueOf(failure.getOrDefault("call", ""));
                System.out.println("  * " + failure.getOrDefault("step", "unknown") + " (" + call + ")");
            }
        }
    }

    private static String summarizeSuccess(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "Completed with no payload.";
        }
        Object status = data.get("status");
        if (status != null) {
            return "status=" + status;
        }
        Object results = data.get("results");
        if (results instanceof List<?> list) {
            return "results=" + list.size();
        }
        Object matches = data.get("matches");
        if (matches instanceof List<?> list) {
            return "matches=" + list.size();
        }
        return "fields=" + new ArrayList<>(data.keySet());
    }

    private static String summarizeErrors(List<ToolError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "No error details available.";
        }
        ToolError first = errors.get(0);
        return first.code() + ": " + first.message();
    }
}
