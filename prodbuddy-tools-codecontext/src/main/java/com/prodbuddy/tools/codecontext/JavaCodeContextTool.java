package com.prodbuddy.tools.codecontext;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JavaCodeContextTool implements Tool {

    private static final String NAME = "codecontext";
    private final JavaProjectSummaryService summaryService;
    private final JavaCodeSearchService searchService;
    private final JavaGraphExtractor graphExtractor;
    private final LocalGraphDbService graphDbService;
    private final CodeQueryIntentParser intentParser;
    private final CodeContextRetrievalFacade retrievalFacade;
    private final CodeContextRanker ranker;
    private final IncidentCorrelationService incidentCorrelationService;

    public JavaCodeContextTool(
            JavaProjectSummaryService summaryService,
            JavaCodeSearchService searchService,
            JavaGraphExtractor graphExtractor,
            LocalGraphDbService graphDbService
    ) {
        this(
                summaryService,
                searchService,
                graphExtractor,
                graphDbService,
                new CodeQueryIntentParser(),
                new CodeContextRetrievalFacade(searchService, graphDbService),
                new CodeContextRanker(),
                new IncidentCorrelationService()
        );
    }

    JavaCodeContextTool(
            JavaProjectSummaryService summaryService,
            JavaCodeSearchService searchService,
            JavaGraphExtractor graphExtractor,
            LocalGraphDbService graphDbService,
            CodeQueryIntentParser intentParser,
            CodeContextRetrievalFacade retrievalFacade,
            CodeContextRanker ranker,
            IncidentCorrelationService incidentCorrelationService
    ) {
        this.summaryService = summaryService;
        this.searchService = searchService;
        this.graphExtractor = graphExtractor;
        this.graphDbService = graphDbService;
        this.intentParser = intentParser;
        this.retrievalFacade = retrievalFacade;
        this.ranker = ranker;
        this.incidentCorrelationService = incidentCorrelationService;
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                NAME,
                "Java project context and code search tool",
                Set.of(
                        "code.summary",
                        "code.search",
                        "code.p1_context",
                        "code.build_graph_db",
                        "code.refresh_graph_db",
                        "code.context_from_query",
                        "code.incident_report",
                        "code.query_graph",
                        "code.p1_tool_calls"
                )
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        String intent = request.intent().toLowerCase();
        return intent.contains("code") || intent.contains("context") || intent.contains("repo");
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        Path projectPath = projectPath(request.payload());
        String operation = request.operation().toLowerCase();
        ToolResponse response = tryExecuteKnown(operation, projectPath, request.payload(), context);
        if (response != null) {
            return response;
        }
        return ToolResponse.failure(
                "CODE_OPERATION",
                "supported operations: summary, search, p1_context, build_graph_db, refresh_graph_db, context_from_query, incident_report, query_graph, p1_tool_calls"
        );
    }

    private ToolResponse tryExecuteKnown(
            String operation,
            Path projectPath,
            Map<String, Object> payload,
            ToolContext context
    ) {
        if ("summary".equals(operation)) {
            return ToolResponse.ok(summaryService.summarize(projectPath));
        }
        if ("search".equals(operation)) {
            return ToolResponse.ok(Map.of("matches", search(projectPath, payload, context)));
        }
        if ("p1_context".equals(operation)) {
            return ToolResponse.ok(p1Context(projectPath, payload, context));
        }
        if ("build_graph_db".equals(operation)) {
            return ToolResponse.ok(buildGraphDb(projectPath, payload, context));
        }
        if ("refresh_graph_db".equals(operation)) {
            return ToolResponse.ok(refreshGraphDb(projectPath, payload, context));
        }
        if ("context_from_query".equals(operation)) {
            return ToolResponse.ok(contextFromQuery(projectPath, payload, context));
        }
        if ("incident_report".equals(operation)) {
            return ToolResponse.ok(incidentReport(projectPath, payload, context));
        }
        if ("query_graph".equals(operation)) {
            return ToolResponse.ok(queryGraph(payload, context));
        }
        if ("p1_tool_calls".equals(operation)) {
            return ToolResponse.ok(p1ToolCalls(projectPath, payload, context));
        }
        return null;
    }

    private Path projectPath(Map<String, Object> payload) {
        String projectPath = String.valueOf(payload.getOrDefault("projectPath", ""));
        if (projectPath.isBlank()) {
            return Path.of(".");
        }
        return Path.of(projectPath);
    }

    private List<Map<String, Object>> search(Path projectPath, Map<String, Object> payload, ToolContext context) {
        String query = String.valueOf(payload.getOrDefault("query", ""));
        int max = Integer.parseInt(context.envOrDefault("CODE_CONTEXT_MAX_RESULTS", "20"));
        return searchService.search(projectPath, query, max);
    }

    private Map<String, Object> buildGraphDb(Path projectPath, Map<String, Object> payload, ToolContext context) {
        JavaGraphSnapshot snapshot = graphExtractor.extract(projectPath);
        Path dbPath = dbPath(payload, context);
        return graphDbService.build(dbPath, snapshot);
    }

    private Map<String, Object> refreshGraphDb(Path projectPath, Map<String, Object> payload, ToolContext context) {
        JavaGraphSnapshot snapshot = graphExtractor.extract(projectPath);
        Path dbPath = dbPath(payload, context);
            String fingerprint = new JavaCodeFingerprinter().fingerprint(projectPath);
        boolean forceRefresh = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("forceRefresh", "false")));
        return graphDbService.refresh(dbPath, snapshot, fingerprint, forceRefresh);
    }

    private Map<String, Object> queryGraph(Map<String, Object> payload, ToolContext context) {
        Path dbPath = dbPath(payload, context);
        String sql = String.valueOf(payload.getOrDefault("sql", "SELECT * FROM ClassNode LIMIT 20"));
        int maxRows = Integer.parseInt(context.envOrDefault("CODE_CONTEXT_MAX_RESULTS", "20"));
        return graphDbService.query(dbPath, sql, maxRows);
    }

    private Map<String, Object> contextFromQuery(Path projectPath, Map<String, Object> payload, ToolContext context) {
        String query = String.valueOf(payload.getOrDefault("query", "")).trim();
        if (query.isBlank()) {
            return Map.of("error", "query must be provided");
        }
        int max = Integer.parseInt(context.envOrDefault("CODE_CONTEXT_MAX_RESULTS", "20"));
        Path dbPath = dbPath(payload, context);
        CodeQueryIntent intent = intentParser.parse(query);
        Map<String, Object> retrieval = retrievalFacade.retrieve(projectPath, dbPath, query, max * 3);
        List<Map<String, Object>> primaryMatches = asMatches(retrieval.get("primaryMatches"));
        List<Map<String, Object>> rankedFindings = ranker.rank(primaryMatches, intent, max);
        return Map.of(
                "intent", intent.toMap(),
                "primaryMatches", primaryMatches,
                "relatedSymbols", retrieval.getOrDefault("relatedSymbols", List.of()),
                "graphContext", retrieval.getOrDefault("graphContext", Map.of("available", false)),
                "rankedFindings", rankedFindings,
                "explainability", explainability(intent, rankedFindings),
                "nextActions", nextActions(projectPath, dbPath, query)
        );
    }

    private Map<String, Object> incidentReport(Path projectPath, Map<String, Object> payload, ToolContext context) {
        String query = String.valueOf(payload.getOrDefault("query", "")).trim();
        if (query.isBlank()) {
            return Map.of("error", "query must be provided");
        }
        Path dbPath = dbPath(payload, context);
        CodeQueryIntent intent = intentParser.parse(query);
        Map<String, Object> contextBundle = contextFromQuery(projectPath, payload, context);
        return incidentCorrelationService.build(query, intent, contextBundle, projectPath, dbPath);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMatches(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return (List<Map<String, Object>>) (List<?>) list;
    }

    private Map<String, Object> explainability(CodeQueryIntent intent, List<Map<String, Object>> rankedFindings) {
        return Map.of(
                "confidence", intent.confidence(),
                "usedSignals", List.of("text_match", "expanded_terms", "entity_hits"),
                "topFindingCount", rankedFindings.size(),
                "queryCategory", intent.category()
        );
    }

    private List<Map<String, Object>> nextActions(Path projectPath, Path dbPath, String query) {
        return List.of(
                Map.of("operation", "refresh_graph_db", "payload", Map.of("projectPath", projectPath.toString(), "dbPath", dbPath.toString())),
                Map.of("operation", "search", "payload", Map.of("projectPath", projectPath.toString(), "query", query)),
                Map.of("operation", "query_graph", "payload", Map.of("dbPath", dbPath.toString(), "sql", "SELECT * FROM MethodNode LIMIT 20"))
        );
    }

    private Map<String, Object> p1Context(Path projectPath, Map<String, Object> payload, ToolContext context) {
        String symptom = String.valueOf(payload.getOrDefault("symptom", "error"));
        List<Map<String, Object>> matches = searchService.search(projectPath, symptom, 20);
        return Map.of(
                "project", summaryService.summarize(projectPath),
                "codeMatches", matches,
                "recommendedQueries", recommendedQueries(symptom),
                "toolCalls", p1ToolCalls(projectPath, payload, context)
        );
    }

    private Map<String, Object> p1ToolCalls(Path projectPath, Map<String, Object> payload, ToolContext context) {
        String symptom = String.valueOf(payload.getOrDefault("symptom", "error"));
        Path dbPath = dbPath(payload, context);
        return Map.of(
                "step1", Map.of("intent", "codecontext", "operation", "build_graph_db", "payload", Map.of("projectPath", projectPath.toString(), "dbPath", dbPath.toString())),
                "step2", Map.of("intent", "codecontext", "operation", "query_graph", "payload", Map.of("dbPath", dbPath.toString(), "sql", "SELECT name, COUNT(*) AS methodCount FROM MethodNode GROUP BY name ORDER BY methodCount DESC")),
                "step3", Map.of("intent", "newrelic", "operation", "query_metrics", "payload", Map.of("metric", "errors", "timeWindowMinutes", 15)),
                "step4", Map.of("intent", "splunk", "operation", "oneshot", "payload", Map.of("search", "search \"" + symptom + "\" | head 50")),
                "step5", Map.of("intent", "elasticsearch", "operation", "analyze", "payload", Map.of("field", "message", "value", symptom, "size", 50))
        );
    }

    private Path dbPath(Map<String, Object> payload, ToolContext context) {
        String dbPath = String.valueOf(payload.getOrDefault("dbPath", context.envOrDefault("CODE_CONTEXT_DB_PATH", ".prodbuddy/codegraph")));
        return Path.of(dbPath);
    }

    private Map<String, Object> recommendedQueries(String symptom) {
        return Map.of(
                "newrelic", Map.of("intent", "newrelic", "operation", "query_metrics", "payload", Map.of("metric", "errors", "timeWindowMinutes", 15)),
                "splunk", Map.of("intent", "splunk", "operation", "oneshot", "payload", Map.of("search", "search \"" + symptom + "\" | head 50")),
                "elasticsearch", Map.of("intent", "elasticsearch", "operation", "analyze", "payload", Map.of("field", "message", "value", symptom, "size", 50))
        );
    }
}
