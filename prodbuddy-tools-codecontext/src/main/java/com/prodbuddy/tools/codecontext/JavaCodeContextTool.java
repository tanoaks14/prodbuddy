package com.prodbuddy.tools.codecontext;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

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
    private final DeadCodeDetector deadCodeDetector;
    private final ChangeImpactAnalyzer changeImpactAnalyzer;
    private final ComplexityAnalyzer complexityAnalyzer;
    private final SequenceLogger seqLog;

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
                new IncidentCorrelationService(),
                new DeadCodeDetector(new LocalGraphQueries()),
                new ChangeImpactAnalyzer(new LocalGraphQueries()),
                new ComplexityAnalyzer(new LocalGraphQueries())
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
            IncidentCorrelationService incidentCorrelationService,
            DeadCodeDetector deadCodeDetector,
            ChangeImpactAnalyzer changeImpactAnalyzer,
            ComplexityAnalyzer complexityAnalyzer
    ) {
        this.summaryService = summaryService;
        this.searchService = searchService;
        this.graphExtractor = graphExtractor;
        this.graphDbService = graphDbService;
        this.intentParser = intentParser;
        this.retrievalFacade = retrievalFacade;
        this.ranker = ranker;
        this.incidentCorrelationService = incidentCorrelationService;
        this.deadCodeDetector = deadCodeDetector;
        this.changeImpactAnalyzer = changeImpactAnalyzer;
        this.complexityAnalyzer = complexityAnalyzer;
        this.seqLog = new Slf4jSequenceLogger(JavaCodeContextTool.class);
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                NAME,
                "Java project context and code search tool",
                Set.of("code.summary", "code.search", "code.p1_context", "code.build_graph_db",
                       "code.refresh_graph_db", "code.context_from_query", "code.incident_report",
                       "code.query_graph", "code.p1_tool_calls", "code.dead_code",
                       "code.change_impact", "code.complexity_report")
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        String intent = request.intent().toLowerCase();
        return intent.contains("code") || intent.contains("context") || intent.contains("repo");
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        seqLog.logSequence("AgentLoopOrchestrator", "codecontext", "execute", "CodeContext " + request.operation());
        Path projectPath = projectPath(request.payload());
        String operation = request.operation().toLowerCase();
        ToolResponse response = tryExecuteKnown(operation, projectPath, request.payload(), context);
        if (response != null) {
            seqLog.logSequence("codecontext", "AgentLoopOrchestrator", "execute", "Completed " + operation);
            return response;
        }
        return ToolResponse.failure(
                "CODE_OPERATION",
                "supported operations: summary, search, p1_context, build_graph_db, refresh_graph_db, context_from_query, incident_report, query_graph, p1_tool_calls"
        );
    }

    private ToolResponse tryExecuteKnown(String operation, Path projectPath, Map<String, Object> payload, ToolContext context) {
        ToolResponse res = executeAnalysis(operation, payload, context);
        if (res != null) {
            return res;
        }
        return switch (operation) {
            case "summary" -> ToolResponse.ok(summaryService.summarize(projectPath));
            case "search" -> ToolResponse.ok(Map.of("matches", search(projectPath, payload, context)));
            case "p1_context" -> ToolResponse.ok(p1Context(projectPath, payload, context));
            case "build_graph_db" -> ToolResponse.ok(buildGraphDb(projectPath, payload, context));
            case "refresh_graph_db" -> ToolResponse.ok(refreshGraphDb(projectPath, payload, context));
            case "context_from_query" -> ToolResponse.ok(contextFromQuery(projectPath, payload, context));
            case "incident_report" -> ToolResponse.ok(incidentReport(projectPath, payload, context));
            case "p1_tool_calls" -> ToolResponse.ok(p1ToolCalls(projectPath, payload, context));
            default -> null;
        };
    }

    private ToolResponse executeAnalysis(String operation, Map<String, Object> payload, ToolContext context) {
        return switch (operation) {
            case "query_graph" -> ToolResponse.ok(queryGraph(payload, context));
            case "dead_code" -> ToolResponse.ok(deadCodeDetector.detect(dbPath(payload, context)).toMap());
            case "change_impact" -> ToolResponse.ok(changeImpact(payload, context));
            case "complexity_report" -> ToolResponse.ok(complexityReport(payload, context));
            default -> null;
        };
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
                Map.of("operation", "refresh_graph_db", "payload", 
                       Map.of("projectPath", projectPath.toString(), "dbPath", dbPath.toString())),
                Map.of("operation", "search", "payload", Map.of("projectPath", projectPath.toString(), "query", query)),
                Map.of("operation", "query_graph", "payload", 
                       Map.of("dbPath", dbPath.toString(), "sql", "SELECT * FROM MethodNode LIMIT 20"))
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

    private Map<String, Object> p1ToolCalls(Path projectPath, Map<String, Object> p, ToolContext ctx) {
        String s = String.valueOf(p.getOrDefault("symptom", "error"));
        Path db = dbPath(p, ctx);
        return Map.of(
                "step1", Map.of("intent", "codecontext", "operation", "build_graph_db", "payload", 
                                Map.of("projectPath", projectPath.toString(), "dbPath", db.toString())),
                "step2", Map.of("intent", "codecontext", "operation", "query_graph", "payload", 
                                Map.of("dbPath", db.toString(), "sql", "SELECT name FROM MethodNode LIMIT 20")),
                "step3", Map.of("intent", "newrelic", "operation", "query_metrics", "payload", 
                                Map.of("metric", "errors", "timeWindowMinutes", 15)),
                "step4", Map.of("intent", "splunk", "operation", "oneshot", "payload", 
                                Map.of("search", "search \"" + s + "\"")),
                "step5", Map.of("intent", "elasticsearch", "operation", "analyze", "payload", 
                                Map.of("field", "message", "value", s))
        );
    }

    private Path dbPath(Map<String, Object> payload, ToolContext context) {
        String dbPath = String.valueOf(payload.getOrDefault("dbPath", context.envOrDefault("CODE_CONTEXT_DB_PATH", ".prodbuddy/codegraph")));
        return Path.of(dbPath);
    }

    private Map<String, Object> changeImpact(Map<String, Object> payload, ToolContext context) {
        String target = String.valueOf(payload.getOrDefault("className", "")).trim();
        if (target.isBlank()) {
            return Map.of("error", "className is required");
        }
        int depth = Integer.parseInt(String.valueOf(payload.getOrDefault("maxDepth", "3")));
        Path dbPath = dbPath(payload, context);
        return changeImpactAnalyzer.analyze(dbPath, target, depth).toMap();
    }

    private Map<String, Object> complexityReport(Map<String, Object> payload, ToolContext context) {
        int topN = Integer.parseInt(String.valueOf(payload.getOrDefault("topN", "20")));
        return complexityAnalyzer.heatmap(dbPath(payload, context), topN);
    }

    private Map<String, Object> recommendedQueries(String s) {
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
