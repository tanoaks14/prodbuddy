package com.prodbuddy.tools.agent;

import com.prodbuddy.core.agent.AgentConfig;
import com.prodbuddy.core.agent.OllamaAgentClient;
import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.core.tool.ToolStyling;

import java.util.Map;
import java.util.Set;

/**
 * Tool for intermediate agent reasoning and analysis within recipes.
 */
public final class AgentTool implements Tool {

    private final com.prodbuddy.core.agent.OllamaAgentClient client = new com.prodbuddy.core.agent.OllamaAgentClient();
    private final com.prodbuddy.observation.SequenceLogger seqLog;

    public AgentTool() {
        this(com.prodbuddy.observation.ObservationContext.getLogger());
    }

    public AgentTool(com.prodbuddy.observation.SequenceLogger seqLog) {
        this.seqLog = seqLog;
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                "agent",
                "Advanced reasoning and analysis tool powered by LLM.",
                Set.of("agent.generate_recipe", "agent.validate_recipe",
                        "agent.think", "agent.extract", "agent.wait",
                        "agent.loop")
        );
    }

    @Override
    public boolean supports(final ToolRequest request) {
        return metadata().capabilities().contains(request.operation());
    }

    @Override
    public ToolStyling styling() {
        return new ToolStyling("#D1C4E9", "#4A148C", "#F3E5F5");
    }

    @Override
    public ToolResponse execute(final ToolRequest request,
                                final ToolContext context) {
        return switch (request.operation()) {
            case "think" -> handleThink(request, context);
            case "extract" -> handleExtract(request, context);
            case "wait" -> handleWait(request, context);
            case "loop" -> handleLoop(request, context);
            case "generate_recipe" -> handleGenerateRecipe(request, context);
            case "validate_recipe" -> handleValidateRecipe(request, context);
            default -> ToolResponse.failure("UNKNOWN_OPERATION",
                    "Operation not supported: " + request.operation());
        };
    }

    private ToolResponse handleLoop(final ToolRequest request,
                                    final ToolContext context) {
        AgentConfig config = AgentConfig.from(context.environment());
        if (!config.enabled()) {
            return ToolResponse.failure("AGENT_DISABLED",
                    "Agent loop is not enabled.");
        }

        String prompt = String.valueOf(request.payload()
                .getOrDefault("prompt", ""));
        Object toolsObj = request.payload().get("tools");
        java.util.List<String> allowedTools = null;
        if (toolsObj instanceof java.util.List) {
            allowedTools = (java.util.List<String>) toolsObj;
        }

        AgentLoopManager manager = new AgentLoopManager(client, config);
        return manager.run(prompt, allowedTools, context);
    }

    private ToolResponse handleWait(final ToolRequest request,
                                    final ToolContext context) {
        Object secondsObj = request.payload().getOrDefault("seconds", "5");
        int seconds = 5;
        try {
            seconds = Integer.parseInt(String.valueOf(secondsObj));
        } catch (NumberFormatException e) {
            // Fallback to 5
        }
        try {
            Thread.sleep(seconds * 1000L);
            return ToolResponse.ok(Map.of("waited_seconds", seconds,
                    "status", "waited"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResponse.failure("INTERRUPTED", "Wait interrupted.");
        }
    }

    private ToolResponse handleExtract(final ToolRequest request,
                                       final ToolContext context) {
        AgentConfig config = AgentConfig.from(context.environment());
        if (!config.enabled()) {
            return ToolResponse.failure("AGENT_DISABLED",
                    "Agent is disabled.");
        }

        String prompt = buildExtractionPrompt(request);
        try {
            String value = client.generate(prompt, config).trim();
            if (value.contains("\n")) {
                value = value.split("\n")[0].trim();
            }
            return ToolResponse.ok(Map.of("sid", value, "value", value,
                    "status", "extracted"));
        } catch (Exception e) {
            return ToolResponse.failure("LLM_ERROR",
                    "Extraction failed: " + e.getMessage());
        }
    }

    private String buildExtractionPrompt(final ToolRequest request) {
        String target = String.valueOf(request.payload()
                .getOrDefault("target", "SID"));
        String data = String.valueOf(request.payload()
                .getOrDefault("data", ""));
        return "You are a data extraction tool. Extract the " + target
                + " from the following data.\n"
                + "Data: " + data + "\n\n"
                + "Return ONLY the raw value, no explanation, no quotes.";
    }

    private ToolResponse handleThink(final ToolRequest request,
                                     final ToolContext context) {
        AgentConfig config = AgentConfig.from(context.environment());
        if (!config.enabled()) {
            return ToolResponse.failure("AGENT_DISABLED",
                    "Agent reasoning is not enabled.");
        }

        String prompt = buildPrompt(request, context);
        java.util.List<String> images = extractImages(request.payload());
        try {
            String opinion = client.generate(prompt, images, config);
            Map<String, String> meta = new java.util.HashMap<>(styling().toMetadata());
            meta.put("style", "thinking");
            meta.put("noteText", "Agent Opinion:\n" + opinion);
            
            seqLog.logSequence("agent", "AgentLoopOrchestrator", "think", "Opinion", meta);
            return ToolResponse.ok(Map.of("opinion", opinion,
                     "status", "analyzed"));
        } catch (Exception e) {
            return ToolResponse.failure("LLM_ERROR",
                    "Failed to generate opinion: " + e.getMessage());
        }
    }

    private java.util.List<String> extractImages(final Map<String, Object> payload) {
        Object img = payload.get("images");
        if (img instanceof java.util.List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        Object single = payload.get("image");
        if (single != null) {
            return java.util.List.of(String.valueOf(single));
        }
        return java.util.Collections.emptyList();
    }

    private ToolResponse handleGenerateRecipe(final ToolRequest request,
                                              final ToolContext context) {
        String obj = String.valueOf(request.payload()
                .getOrDefault("objective", "A diagnostic recipe"));
        String guide = readGuide(context);
        if (guide == null) {
            return ToolResponse.failure("GUIDE_NOT_FOUND", "No guide.");
        }
        AgentConfig config = AgentConfig.from(context.environment());
        return new RecipeAgentHelper(client, config).generate(obj,
                guide, context);
    }

    private ToolResponse handleValidateRecipe(final ToolRequest request,
                                              final ToolContext context) {
        String content = String.valueOf(request.payload()
                .getOrDefault("recipe", ""));
        if (content.isBlank()) {
            return ToolResponse.failure("MISSING_CONTENT", "No content.");
        }
        AgentConfig config = AgentConfig.from(context.environment());
        return new RecipeAgentHelper(client, config).validateContent(content,
                context);
    }

    private String readGuide(final ToolContext context) {
        String path = context.envOrDefault("RECIPE_GUIDE_PATH",
                "docs/creating-recipes-guide.md");
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private String buildPrompt(final ToolRequest request,
                               final ToolContext context) {
        Object custom = request.payload().get("prompt");
        StringBuilder sb = new StringBuilder("Assistant Context:\n");
        if (custom != null && !String.valueOf(custom).isBlank()) {
            sb.append("Task: ").append(custom).append("\n");
        }
        request.payload().forEach((k, v) -> {
            if (!"prompt".equals(k) && !"image".equals(k) && !"images".equals(k)) {
                sb.append("- ").append(k).append(": ").append(v).append("\n");
            }
        });
        return sb.append("\nConcise recommendation:").toString();
    }
}
