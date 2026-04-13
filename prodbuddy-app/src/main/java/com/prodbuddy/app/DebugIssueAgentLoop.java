package com.prodbuddy.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolError;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

public final class DebugIssueAgentLoop {

    private final DebugToolExecutor executor;

    public DebugIssueAgentLoop(DebugToolExecutor executor) {
        this.executor = executor;
    }

    public ToolResponse run(String issueQuery, ToolContext context) {
        List<DebugStep> plan = plan(issueQuery, context);
        Map<String, Object> stepResults = new LinkedHashMap<>();
        List<Map<String, Object>> stepFailures = new ArrayList<>();

        for (DebugStep step : plan) {
            ToolResponse response = safeExecute(step.request(), context);
            if (response.success()) {
                stepResults.put(step.name(), response.data());
                continue;
            }
            stepFailures.add(failure(step.name(), response.errors()));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("issue", issueQuery);
        report.put("status", stepFailures.isEmpty() ? "healthy" : "attention_required");
        report.put("successfulSteps", stepResults.size());
        report.put("failedSteps", stepFailures.size());
        report.put("steps", stepResults);
        report.put("failures", stepFailures);
        report.put("nextActions", nextActions(stepFailures));
        return ToolResponse.ok(report);
    }

    private List<DebugStep> plan(String issueQuery, ToolContext context) {
        String projectPath = context.envOrDefault("PRODBUDDY_PROJECT_PATH", System.getProperty("user.dir"));
        String dbPath = context.envOrDefault("CODE_CONTEXT_DB_PATH", ".prodbuddy/codegraph");
        int windowMinutes = Integer.parseInt(context.envOrDefault("DEBUG_WINDOW_MINUTES", "15"));
        int limit = Integer.parseInt(context.envOrDefault("DEBUG_RESULT_LIMIT", "100"));

        return List.of(
                new DebugStep("system_tools", new ToolRequest("system", "list_tools", Map.of())),
                new DebugStep("system_agent_config", new ToolRequest("system", "agent_config", Map.of())),
                codeContextStep(projectPath, dbPath, issueQuery),
                newRelicStep(windowMinutes, limit),
                splunkStep(issueQuery, limit),
                elasticsearchStep(issueQuery, limit),
                kubectlPreviewStep()
        );
    }

    private DebugStep codeContextStep(String projectPath, String dbPath, String issueQuery) {
        return new DebugStep("code_p1_context", new ToolRequest(
                "codecontext",
                "p1_context",
                Map.of("projectPath", projectPath, "dbPath", dbPath, "symptom", issueQuery)
        ));
    }

    private DebugStep newRelicStep(int windowMinutes, int limit) {
        return new DebugStep("newrelic_errors", new ToolRequest(
                "newrelic",
                "query_metrics",
                Map.of("metric", "errors", "timeWindowMinutes", windowMinutes, "limit", limit)
        ));
    }

    private DebugStep splunkStep(String issueQuery, int limit) {
        return new DebugStep("splunk_search", new ToolRequest(
                "splunk",
                "oneshot",
                Map.of("search", "search \"" + issueQuery + "\" | head " + limit)
        ));
    }

    private DebugStep elasticsearchStep(String issueQuery, int limit) {
        return new DebugStep("elasticsearch_search", new ToolRequest(
                "elasticsearch",
                "query",
                Map.of("index", "logs-*", "queryString", issueQuery, "size", limit)
        ));
    }

    private DebugStep kubectlPreviewStep() {
        return new DebugStep("kubectl_pods_preview", new ToolRequest(
                "kubectl",
                "get",
                Map.of("resource", "pods", "args", List.of("-A"), "execute", false)
        ));
    }

    private Map<String, Object> failure(String stepName, List<ToolError> errors) {
        List<Map<String, String>> normalized = errors.stream()
                .map(error -> Map.of("code", error.code(), "message", error.message()))
                .toList();
        return Map.of("step", stepName, "errors", normalized);
    }

    private ToolResponse safeExecute(ToolRequest request, ToolContext context) {
        try {
            return executor.execute(request, context);
        } catch (Exception exception) {
            String message = exception.getMessage();
            if (message == null || message.isBlank()) {
                message = exception.getClass().getSimpleName();
            }
            return ToolResponse.failure("DEBUG_STEP_EXCEPTION", message);
        }
    }

    private List<String> nextActions(List<Map<String, Object>> failures) {
        if (failures.isEmpty()) {
            return List.of("No action required. All debug-loop steps succeeded.");
        }
        List<String> actions = new ArrayList<>();
        actions.add("Review failed steps and fix configuration/auth for the corresponding integrations.");
        actions.add("Re-run with --debug-issue once telemetry credentials and endpoints are confirmed.");
        actions.add("Use code_p1_context output to prioritize likely root-cause symbols first.");
        return actions;
    }

    private record DebugStep(String name, ToolRequest request) {

    }

    @FunctionalInterface
    public interface DebugToolExecutor {

        ToolResponse execute(ToolRequest request, ToolContext context);
    }
}
