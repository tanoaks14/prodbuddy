package com.prodbuddy.tools.agent;

import com.prodbuddy.core.agent.AgentConfig;
import com.prodbuddy.core.agent.OllamaAgentClient;
import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

import java.util.Map;

/**
 * Tool for intermediate agent reasoning and analysis within recipes.
 */
public final class AgentTool implements Tool {

    private final OllamaAgentClient client = new OllamaAgentClient();

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                "agent",
                "Advanced reasoning and analysis tool powered by LLM.",
                java.util.Set.of("think", "generate_recipe")
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        return metadata().capabilities().contains(request.operation());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        return switch (request.operation()) {
            case "think" -> handleThink(request, context);
            case "generate_recipe" -> handleGenerateRecipe(request, context);
            default -> ToolResponse.failure("UNKNOWN_OPERATION", "Operation not supported: " + request.operation());
        };
    }

    private ToolResponse handleThink(ToolRequest request, ToolContext context) {
        AgentConfig config = AgentConfig.from(context.environment());
        if (!config.enabled()) {
            return ToolResponse.failure("AGENT_DISABLED", "Agent reasoning is not enabled in environment.");
        }

        String prompt = buildPrompt(request, context);
        try {
            String opinion = client.generate(prompt, config);
            return ToolResponse.ok(Map.of(
                    "opinion", opinion,
                    "status", "analyzed"
            ));
        } catch (Exception e) {
            return ToolResponse.failure("LLM_ERROR", "Failed to generate agent opinion: " + e.getMessage());
        }
    }

    private ToolResponse handleGenerateRecipe(ToolRequest request, ToolContext context) {
        String objective = String.valueOf(request.payload().getOrDefault("objective", "A general diagnostic recipe"));
        String guide = readGuide(context);
        if (guide == null) return ToolResponse.failure("GUIDE_NOT_FOUND", "Could not read recipe guide.");

        String prompt = buildGenerationPrompt(objective, guide);
        AgentConfig config = AgentConfig.from(context.environment());
        try {
            String recipe = client.generate(prompt, config);
            validateRecipe(recipe);
            String path = saveRecipe(recipe, context);
            return ToolResponse.ok(java.util.Map.of("recipe", recipe, "path", path, "status", "generated_and_saved"));
        } catch (Exception e) {
            return ToolResponse.failure("GENERATION_FAILED", "Recipe invalid or failed: " + e.getMessage());
        }
    }

    private void validateRecipe(String content) throws Exception {
        java.nio.file.Path temp = java.nio.file.Files.createTempFile("gen-recipe-val", ".md");
        try {
            java.nio.file.Files.writeString(temp, content);
            new com.prodbuddy.recipes.RecipeLoader().load(temp);
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }

    private String readGuide(ToolContext context) {
        String path = context.envOrDefault("RECIPE_GUIDE_PATH", "docs/creating-recipes-guide.md");
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private String buildGenerationPrompt(String objective, String guide) {
        return "You are the ProdBuddy Recipe Generator.\n\n"
                + "OBJECTIVE: " + objective + "\n\n"
                + "CONSTRAINTS:\n- Produce a VALID ProdBuddy Recipe in Markdown format.\n"
                + "- Use exact tool names and operations from the GUIDE.\n"
                + "- MANDATORY: Every step MUST include 'tool: <name>' and 'operation: <op>' as separate keys.\n"
                + "- Use ${stepName.field} for interpolation.\n- Return ONLY the markdown content.\n\n"
                + "GUIDE HIGHLIGHTS:\n" + distillGuide(guide) + "\n\nGENERATE:";
    }

    private String saveRecipe(String content, ToolContext context) throws java.io.IOException {
        String dir = context.envOrDefault("RECIPES_DIR", "recipes");
        String fileName = "generated-" + System.currentTimeMillis() + ".md";
        java.nio.file.Path path = java.nio.file.Path.of(dir).resolve(fileName);
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, content);
        return path.toString();
    }

    private String distillGuide(String guide) {
        String marker = "## AI Generator Reference";
        int index = guide.indexOf(marker);
        if (index >= 0) {
            return guide.substring(index); // Prioritize the rulebook
        }
        
        StringBuilder sb = new StringBuilder();
        for (String line : guide.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.startsWith("-") || trimmed.startsWith("```")) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildPrompt(ToolRequest request, ToolContext context) {
        Object customPrompt = request.payload().get("prompt");
        StringBuilder sb = new StringBuilder();
        sb.append("You are the ProdBuddy Diagnostic Assistant.\n\n");
        
        if (customPrompt != null && !String.valueOf(customPrompt).isBlank()) {
            sb.append("Focus Task: ").append(customPrompt).append("\n\n");
        } else {
            sb.append("Analyze the provided diagnostic data and recommend the most effective next step.\n\n");
        }

        sb.append("Current Context Values:\n");
        request.payload().forEach((k, v) -> {
            if (!"prompt".equals(k)) {
                sb.append("- ").append(k).append(": ").append(v).append("\n");
            }
        });

        sb.append("\nPlease provide a concise opinion followed by a single clear recommendation.");
        return sb.toString();
    }
}
