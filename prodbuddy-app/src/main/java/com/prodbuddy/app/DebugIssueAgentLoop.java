package com.prodbuddy.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolError;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

public final class DebugIssueAgentLoop {

    private final DebugToolExecutor executor;
    private final DebugProgressListener progressListener;
    private final SequenceLogger seqLog;

    public DebugIssueAgentLoop(DebugToolExecutor executor) {
        this(executor, DebugProgressListener.noop());
    }

    public DebugIssueAgentLoop(DebugToolExecutor executor, DebugProgressListener progressListener) {
        this.executor = executor;
        this.progressListener = progressListener == null ? DebugProgressListener.noop() : progressListener;
        this.seqLog = new Slf4jSequenceLogger(DebugIssueAgentLoop.class);
    }

    public ToolResponse run(String issueQuery, ToolContext context) {
        seqLog.logSequence("Client", "DebugIssueAgentLoop", "run", "Starting debug loop");
        Map<String, Object> stepResults = new LinkedHashMap<>();
        List<Map<String, Object>> stepFailures = new ArrayList<>();
        for (DebugStep step : plan(issueQuery, context)) {
            processStep(step, context, stepResults, stepFailures);
        }
        String status = stepFailures.isEmpty() ? "healthy" : "attention_required";
        seqLog.logSequence("DebugIssueAgentLoop", "Client", "run", "Completed: " + status);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("issue", issueQuery);
        report.put("status", status);
        report.put("successfulSteps", stepResults.size());
        report.put("failedSteps", stepFailures.size());
        report.put("steps", stepResults);
        report.put("failures", stepFailures);
        report.put("nextActions", nextActions(stepFailures));
        return ToolResponse.ok(report);
    }

    private void processStep(
            DebugStep step, ToolContext context,
            Map<String, Object> stepResults,
            List<Map<String, Object>> stepFailures) {
        seqLog.logSequence("DebugIssueAgentLoop", "Orchestrator", "executeStep", "Step: " + step.name());
        progressListener.onStepStart(step.name(), step.request(), step.description());
        ToolResponse response = safeExecute(step.request(), context);
        if (response.success()) {
            stepResults.put(step.name(), response.data());
            progressListener.onStepFinish(step.name(), step.request(), true, summarizeStepData(response.data()));
        } else {
            stepFailures.add(failure(step.name(), response.errors()));
            progressListener.onStepFinish(step.name(), step.request(), false, summarizeErrors(response.errors()));
        }
    }

    private List<DebugStep> plan(String issueQuery, ToolContext context) {
        String projectPath = context.envOrDefault("PRODBUDDY_PROJECT_PATH", System.getProperty("user.dir"));
        String dbPath = context.envOrDefault("CODE_CONTEXT_DB_PATH", ".prodbuddy/codegraph");
        String splunkIndex = context.envOrDefault("SPLUNK_DEFAULT_INDEX", "").trim();
        String splunkAuthMode = context.envOrDefault("SPLUNK_AUTH_MODE", "token").trim().toLowerCase();
        String splunkSessionKey = context.envOrDefault("SPLUNK_SESSION_KEY", "").trim();
        int windowMinutes = Integer.parseInt(context.envOrDefault("DEBUG_WINDOW_MINUTES", "15"));
        int limit = Integer.parseInt(context.envOrDefault("DEBUG_RESULT_LIMIT", "100"));

        return List.of(
                new DebugStep(
                        "system_tools",
                        "List registered tools available for this run",
                        new ToolRequest("system", "list_tools", Map.of())
                ),
                new DebugStep(
                        "system_agent_config",
                        "Show active agent runtime configuration",
                        new ToolRequest("system", "agent_config", Map.of())
                ),
                codeContextStep(projectPath, dbPath, issueQuery),
                newRelicStep(windowMinutes, limit),
                splunkStep(issueQuery, limit, splunkIndex, splunkAuthMode, splunkSessionKey),
                elasticsearchStep(issueQuery, limit),
                kubectlPreviewStep()
        );
    }

    private DebugStep codeContextStep(String projectPath, String dbPath, String issueQuery) {
        return new DebugStep("code_p1_context", "Collect code hotspots related to the issue", new ToolRequest(
                "codecontext",
                "p1_context",
                Map.of("projectPath", projectPath, "dbPath", dbPath, "symptom", issueQuery)
        ));
    }

    private DebugStep newRelicStep(int windowMinutes, int limit) {
        return new DebugStep("newrelic_errors", "Query New Relic error metrics", new ToolRequest(
                "newrelic",
                "query_metrics",
                Map.of("metric", "errors", "timeWindowMinutes", windowMinutes, "limit", limit)
        ));
    }

    private DebugStep splunkStep(
            String issueQuery,
            int limit,
            String splunkIndex,
            String splunkAuthMode,
            String splunkSessionKey
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("terms", issueQuery);
        payload.put("count", limit);
        if (!splunkIndex.isBlank()) {
            payload.put("index", splunkIndex);
        }
        if (!splunkAuthMode.isBlank()) {
            payload.put("authMode", splunkAuthMode);
        }
        if (("sso".equals(splunkAuthMode) || "session".equals(splunkAuthMode)) && !splunkSessionKey.isBlank()) {
            payload.put("sessionKey", splunkSessionKey);
        }
        return new DebugStep("splunk_search", "Search Splunk logs for matching symptoms", new ToolRequest(
                "splunk",
                "oneshot",
                payload
        ));
    }

    private DebugStep elasticsearchStep(String issueQuery, int limit) {
        return new DebugStep("elasticsearch_search", "Search Elasticsearch logs", new ToolRequest(
                "elasticsearch",
                "query",
                Map.of("index", "logs-*", "queryString", issueQuery, "size", limit)
        ));
    }

    private DebugStep kubectlPreviewStep() {
        return new DebugStep("kubectl_pods_preview", "Preview Kubernetes pod status across namespaces", new ToolRequest(
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

    private String summarizeErrors(List<ToolError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "No error details available.";
        }
        ToolError first = errors.get(0);
        return first.code() + ": " + first.message();
    }

    private String summarizeStepData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "Completed with no payload.";
        }
        Object status = data.get("status");
        if (status != null) {
            return "status=" + status;
        }
        Object summary = data.get("summary");
        if (summary != null) {
            return String.valueOf(summary);
        }
        Object results = data.get("results");
        if (results instanceof List<?> list) {
            return "results=" + list.size();
        }
        Object matches = data.get("matches");
        if (matches instanceof List<?> list) {
            return "matches=" + list.size();
        }
        Object tools = data.get("tools");
        if (tools instanceof List<?> list) {
            return "tools=" + list.size();
        }
        return "fields=" + data.keySet();
    }

    private record DebugStep(String name, String description, ToolRequest request) {

    }

    @FunctionalInterface
    public interface DebugProgressListener {

        void onStepStart(String stepName, ToolRequest request, String description);

        default void onStepFinish(String stepName, ToolRequest request, boolean success, String summary) {
            // optional
        }

        static DebugProgressListener noop() {
            return (stepName, request, description) -> {
            };
        }
    }

    @FunctionalInterface
    public interface DebugToolExecutor {

        ToolResponse execute(ToolRequest request, ToolContext context);
    }
}
