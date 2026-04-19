package com.prodbuddy.tools.codecontext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Service to provide tool call recommendations and next actions for code analysis.
 */
public final class ToolRecommendationService {

    public List<Map<String, Object>> nextActions(final Path projectPath, final Path dbPath, final String query) {
        return List.of(
                Map.of("operation", "refresh_graph_db", "payload",
                       Map.of("projectPath", projectPath.toString(), "dbPath", dbPath.toString())),
                Map.of("operation", "search", "payload", Map.of("projectPath", projectPath.toString(), "query", query)),
                Map.of("operation", "query_graph", "payload",
                       Map.of("dbPath", dbPath.toString(), "sql", "SELECT * FROM MethodNode LIMIT 20"))
        );
    }

    public Map<String, Object> p1ToolCalls(final Path proj, final Path db, final String symptom) {
        return Map.of(
                "step1", Map.of("intent", "codecontext", "operation", "build_graph_db", "payload",
                                Map.of("projectPath", proj.toString(), "dbPath", db.toString())),
                "step2", Map.of("intent", "codecontext", "operation", "query_graph", "payload",
                                Map.of("dbPath", db.toString(), "sql", "SELECT name FROM MethodNode LIMIT 20")),
                "step3", Map.of("intent", "newrelic", "operation", "query_metrics", "payload",
                                Map.of("metric", "errors", "timeWindowMinutes", 15)),
                "step4", Map.of("intent", "splunk", "operation", "oneshot", "payload",
                                Map.of("search", "search \"" + symptom + "\"")),
                "step5", Map.of("intent", "elasticsearch", "operation", "analyze", "payload",
                                Map.of("field", "message", "value", symptom))
        );
    }

    public Map<String, Object> recommendedQueries(final String s) {
        return Map.of(
                "newrelic", Map.of("intent", "newrelic", "operation", "query_metrics",
                                   "payload", Map.of("metric", "errors", "timeWindowMinutes", 15)),
                "splunk", Map.of("intent", "splunk", "operation", "oneshot",
                                 "payload", Map.of("search", "search \"" + s + "\"")),
                "elastic", Map.of("intent", "elasticsearch", "operation", "analyze",
                                  "payload", Map.of("field", "message", "value", s))
        );
    }
}
