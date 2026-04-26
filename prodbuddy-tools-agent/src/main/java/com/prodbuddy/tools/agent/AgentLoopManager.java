package com.prodbuddy.tools.agent;

import com.prodbuddy.core.agent.AgentConfig;
import com.prodbuddy.core.agent.OllamaAgentClient;
import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

import java.util.Map;
import java.util.Optional;

public final class AgentLoopManager {

    private final OllamaAgentClient client;
    private final AgentConfig config;
    private final com.prodbuddy.observation.SequenceLogger seqLog;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private java.util.List<String> allowedTools;

    public AgentLoopManager(final OllamaAgentClient client,
                            final AgentConfig config,
                            final com.prodbuddy.observation.SequenceLogger seqLog) {
        this.client = client;
        this.config = config;
        this.seqLog = seqLog;
    }

    public ToolResponse run(final String prompt,
                            final java.util.List<String> allowedTools,
                            final ToolContext context) {
        this.allowedTools = allowedTools != null ? allowedTools
                : java.util.List.of("git", "json", "datetime", "interactive");
        seqLog.logSequence("Agent", "AgentLoopOrchestrator", "run", "Started Orchestration");
        AgentLoopState state = new AgentLoopState(prompt);

        try {
            plan(state);
        } catch (Exception e) {
            return handlePlanningError(e);
        }

        while (state.getStatus() == AgentLoopState.Status.RUNNING) {
            if (state.getIterations() >= 15) {
                state.setStatus(AgentLoopState.Status.FAILED);
                break;
            }
            step(state, context);
        }

        return ToolResponse.ok(Map.of(
            "status", state.getStatus().name(),
            "goal", state.getGoal(),
            "iterations", state.getIterations(),
            "working_log", formatHistory(state.getHistory())
        ));
    }

    private String formatHistory(
            final java.util.List<AgentLoopState.StepLog> history) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            AgentLoopState.StepLog log = history.get(i);
            sb.append("\n### Iteration ").append(i + 1).append("\n");
            sb.append("**Thought**: ").append(log.thought()).append("\n");
            sb.append("**Tool**: `").append(log.tool()).append("`\n");
            sb.append("**Params**: `").append(log.params()).append("`\n");
            sb.append("**Result**:\n```json\n").append(log.result())
                    .append("\n```\n");
        }
        return sb.toString();
    }

    private ToolResponse handlePlanningError(final Exception e) {
        return ToolResponse.failure("PLANNING_FAILED",
                "Failed to plan: " + e.getMessage());
    }

    private void step(final AgentLoopState state, final ToolContext context) {
        try {
            executeNextStep(state, context);
        } catch (Exception e) {
            state.logStep("Error: " + e.getMessage(), "error", Map.of(), "fail");
            state.setStatus(AgentLoopState.Status.FAILED);
        }
    }

    private void plan(final AgentLoopState state) throws Exception {
        String p = "Goal: " + state.getGoal() + "\nTools: " + allowedTools
                + "\nTasks as numbered list:";
        String resp = client.generate(p, config);
        for (String line : resp.split("\n")) {
            String t = line.trim();
            if (!t.isBlank() && Character.isDigit(t.charAt(0))) {
                state.addTask(t);
            }
        }
    }

    private void executeNextStep(final AgentLoopState state,
                                final ToolContext context) throws Exception {
        String h = mapper.writeValueAsString(state.getHistory());
        String t = mapper.writeValueAsString(state.getTasks());
        String p = decidePrompt(state.getGoal(), h, t);

        String json = client.generate(p, config);
        json = cleanJson(json);
        seqLog.logSequence("Agent", "AgentLoopOrchestrator", "decide", "Next action decided");

        Map<String, Object> decision = mapper.readValue(json, Map.class);
        if (Boolean.TRUE.equals(decision.get("finished"))) {
            state.setStatus(AgentLoopState.Status.FINISHED);
            return;
        }

        String thought = (String) decision.get("thought");
        String tool = (String) decision.get("tool");
        String op = (String) decision.get("operation");
        Map<String, Object> params = (Map<String, Object>)
                decision.getOrDefault("params", Map.of());

        String result = callTool(tool, op, params, context);
        state.logStep(thought, tool, params, result);
    }

    private String decidePrompt(final String goal, final String h,
                                final String t) {
        return "GOAL: " + goal + "\nHISTORY: " + h + "\nTASKS: " + t
                + "\nNext action. Respond ONLY with a JSON object.\n"
                + "Allowed tools: " + allowedTools + "\n"
                + "Format: {\"thought\": \"...\", \"tool\": \"...\", "
                + "\"operation\": \"...\", \"params\": {}, "
                + "\"finished\": false}";
    }

    private String cleanJson(final String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1).trim();
        }
        return raw.trim();
    }

    private String callTool(final String name, final String op,
                            final Map<String, Object> params,
                            final ToolContext ctx) throws Exception {
        String displayName = name.substring(0, 1).toUpperCase() + name.substring(1);
        seqLog.logSequence("AgentLoopOrchestrator", displayName, op, "Executing tool");
        Optional<Tool> tool = ctx.registry().find(name);
        if (tool.isPresent()) {
            ToolResponse resp = tool.get().execute(
                    new ToolRequest(name, op, params), ctx);
            String r = mapper.writeValueAsString(resp.data());
            return resp.success() ? r : "FAILED: " + resp.errors();
        }
        return "TOOL_NOT_FOUND: " + name;
    }
}
