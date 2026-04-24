package com.prodbuddy.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.prodbuddy.context.ContextCollector;
import com.prodbuddy.context.ContextFormatter;
import com.prodbuddy.context.ConversationContext;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolError;
import com.prodbuddy.orchestrator.AgentLoopOrchestrator;
import com.prodbuddy.recipes.RecipeToolExecutor;
import com.prodbuddy.recipes.RecipeDefinition;
import com.prodbuddy.recipes.RecipeRegistry;
import com.prodbuddy.recipes.RecipeReport;
import com.prodbuddy.recipes.RecipeRunResult;
import com.prodbuddy.recipes.RecipeRunner;
import com.prodbuddy.recipes.RecipeStepResult;
import com.prodbuddy.recipes.RecipeStepSummarizer;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

/**
 * Handles --run-recipe, --list-recipes, and --add-recipe CLI modes. Dispatches
 * to the appropriate handler based on args[0].
 */
final class RecipeCliHandler {

    private static final SequenceLogger seqLog = new Slf4jSequenceLogger(RecipeCliHandler.class);

    private RecipeCliHandler() {
    }

    static void handle(
            String[] args,
            AgentLoopOrchestrator orchestrator,
            Map<String, String> environment,
            com.prodbuddy.core.agent.AgentConfig agentConfig
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
            com.prodbuddy.core.agent.AgentConfig agentConfig
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
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), env, orchestrator.registry());
        ConversationContext convCtx = new ConversationContext(context.requestId());
        ContextCollector collector = new ContextCollector(orchestrator::run, convCtx);
        
        String fullContent = "";
        try {
            fullContent = java.nio.file.Files.readString(Path.of(dir, name + ".md"));
        } catch (Exception e) {
            seqLog.logSequence("RecipeCliHandler", "FileSystem", "readRecipe", "Could not read raw recipe for system context: " + e.getMessage());
        }

        RecipeRunResult result = new RecipeRunner().run(recipe, fullContent, context, collector);
        printRecipeSteps(result);
        Map<String, Object> summary = RecipeReport.summarize(result);
        printRecipeSummary(summary);
        runRecipeLlm(name, summary, convCtx, agentConfig);
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

    private static void runRecipeLlm(
            String name, Map<String, Object> summary,
            ConversationContext convCtx, com.prodbuddy.core.agent.AgentConfig config
    ) {
        if (!config.enabled() || !"ollama".equalsIgnoreCase(config.provider())) {
            seqLog.logSequence("RecipeCliHandler", "LLM", "runRecipeLlm", "Skipping LLM (Disabled)");
            return;
        }
        seqLog.logSequence("RecipeCliHandler", "LLM", "runRecipeLlm", "Requesting Recipe Analysis");
        String summaryText = String.valueOf(summary).replace('"', '\'');
        String contextText = ContextFormatter.format(convCtx);

        // Write to markdown context file as requested
        String contextFilePath = name + "-context.md";
        writeContextFile(contextFilePath, contextText);

        String prompt = "You are a diagnostic AI assistant. Analyze the following recipe execution.\n"
                + "Recipe: " + name + "\n"
                + "Execution Summary: " + summaryText + "\n\n"
                + contextText + "\n"
                + "Please:\n"
                + "1. Summarize what each tool returned\n"
                + "2. Identify patterns, anomalies or failures\n"
                + "3. Diagnose root causes for any failures\n"
                + "4. Recommend next steps or follow-up checks\n"
                + "Note: For actual exact outputs/responses, tell the user they can refer to the detailed context file: " + contextFilePath;
        String response = new com.prodbuddy.core.agent.OllamaAgentClient().generate(prompt, config);
        seqLog.logSequence("LLM", "RecipeCliHandler", "runRecipeLlm", "Received Analysis");
        System.out.println("\n=== AI Analysis ===");
        System.out.println(TerminalMarkdownRenderer.toTerminalText(response));
    }

    private static void writeContextFile(String contextFilePath, String contextText) {
        try {
            java.nio.file.Files.writeString(Path.of(contextFilePath), contextText);
            System.out.println("Detailed execution context saved to: " + contextFilePath);
        } catch (java.io.IOException e) {
            seqLog.logSequence("RecipeCliHandler", "FileSystem", "writeContext", "Failed to write context file: " + e.getMessage());
        }
    }

    private static void printRecipeSteps(RecipeRunResult result) {
        System.out.println();
        System.out.println("Recipe execution:");
        for (RecipeStepResult step : result.stepResults()) {
            String call = step.tool() + "." + step.operation();
            System.out.println("- " + step.stepName() + " (call: " + call + ")");
            String status = step.response().success() ? "ok" : "failed";
            
            boolean logFull = Boolean.parseBoolean(String.valueOf(step.resolvedParams().getOrDefault("logFullResponse", "false")));
            
            String summary = step.response().success()
                    ? summarizeSuccess(step.response().data())
                    : summarizeErrors(step.response().errors());
            System.out.println("  result: " + status + " - " + summary);

            if (logFull && step.response().success()) {
                Object body = step.response().data().get("body");
                if (body != null) {
                    System.out.println("  [FULL RESPONSE]");
                    System.out.println(body);
                }
            }
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
        return RecipeStepSummarizer.summarize(ToolResponse.ok(data));
    }

    private static String summarizeErrors(List<ToolError> errors) {
        return RecipeStepSummarizer.summarize(new ToolResponse(false, Map.of(), errors));
    }
}
