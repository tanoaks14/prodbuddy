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
                java.util.Set.of("think")
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
