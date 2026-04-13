package com.prodbuddy.tools.codecontext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class IncidentCorrelationService {

    public Map<String, Object> build(
            String query,
            CodeQueryIntent intent,
            Map<String, Object> contextBundle,
            Path projectPath,
            Path dbPath
    ) {
        return Map.of(
                "query", query,
                "intent", intent.toMap(),
                "deterministicStrategy", strategy(),
                "codeContext", contextBundle,
                "telemetryQueries", telemetryQueries(query),
                "correlationRules", correlationRules(),
                "recommendedExecutionOrder", executionOrder(projectPath, dbPath, query)
        );
    }

    private List<String> strategy() {
        return List.of(
                "collect_code_matches",
                "collect_graph_hotspots",
                "collect_metrics_logs",
                "intersect_signals_by_term",
                "rank_root_cause_candidates"
        );
    }

    private Map<String, Object> telemetryQueries(String query) {
        return Map.of(
                "newrelic", Map.of("intent", "newrelic", "operation", "query_metrics", "payload", Map.of("metric", "errors", "timeWindowMinutes", 15)),
                "splunk", Map.of("intent", "splunk", "operation", "oneshot", "payload", Map.of("search", "search \"" + query + "\" | head 100")),
                "elasticsearch", Map.of("intent", "elasticsearch", "operation", "search", "payload", Map.of("index", "logs-*", "queryString", query, "size", 100))
        );
    }

    private List<String> correlationRules() {
        return List.of(
                "same_error_token_in_code_and_logs",
                "stacktrace_symbol_matches_method_or_class",
                "hotspot_method_appears_in_recent_errors",
                "latency_and_error_spike_share_service_identifier"
        );
    }

    private List<Map<String, Object>> executionOrder(Path projectPath, Path dbPath, String query) {
        return List.of(
                Map.of("intent", "codecontext", "operation", "context_from_query", "payload", Map.of("projectPath", projectPath.toString(), "dbPath", dbPath.toString(), "query", query)),
                Map.of("intent", "splunk", "operation", "oneshot", "payload", Map.of("search", "search \"" + query + "\" | head 100")),
                Map.of("intent", "elasticsearch", "operation", "search", "payload", Map.of("index", "logs-*", "queryString", query, "size", 100)),
                Map.of("intent", "newrelic", "operation", "query_metrics", "payload", Map.of("metric", "errors", "timeWindowMinutes", 15))
        );
    }
}
