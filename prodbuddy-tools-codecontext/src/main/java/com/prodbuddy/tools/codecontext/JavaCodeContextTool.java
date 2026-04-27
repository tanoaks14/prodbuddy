package com.prodbuddy.tools.codecontext;

import com.prodbuddy.core.tool.*;
import com.prodbuddy.observation.SequenceLogger;

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
    private final DeadCodeDetector deadCodeDetector;
    private final ChangeImpactAnalyzer changeImpactAnalyzer;
    private final ComplexityAnalyzer complexityAnalyzer;
    private final CallChainService callChainService;
    private final ToolRecommendationService recommendationService;
    private final IncidentDiagnosticService diagnosticService;
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
                new DeadCodeDetector(new LocalGraphQueries()),
                new ChangeImpactAnalyzer(new LocalGraphQueries()),
                new ComplexityAnalyzer(new LocalGraphQueries()),
                new CallChainService(new LocalGraphQueries()),
                new ToolRecommendationService(),
                new IncidentDiagnosticService(
                        new CallChainService(new LocalGraphQueries()),
                        new LocalGitService(),
                        new LocalGraphQueries()
                )
        );
    }

    JavaCodeContextTool(
            JavaProjectSummaryService summaryService,
            JavaCodeSearchService searchService,
            JavaGraphExtractor graphExtractor,
            LocalGraphDbService graphDbService,
            DeadCodeDetector deadCodeDetector,
            ChangeImpactAnalyzer changeImpactAnalyzer,
            ComplexityAnalyzer complexityAnalyzer,
            CallChainService callChainService,
            ToolRecommendationService recommendationService,
            IncidentDiagnosticService diagnosticService
    ) {
        this.summaryService = summaryService;
        this.searchService = searchService;
        this.graphExtractor = graphExtractor;
        this.graphDbService = graphDbService;
        this.deadCodeDetector = deadCodeDetector;
        this.changeImpactAnalyzer = changeImpactAnalyzer;
        this.complexityAnalyzer = complexityAnalyzer;
        this.callChainService = callChainService;
        this.recommendationService = recommendationService;
        this.diagnosticService = diagnosticService;
        this.seqLog = com.prodbuddy.observation.ObservationContext.getLogger();
    }

    @Override
    public com.prodbuddy.core.tool.ToolStyling styling() {
        return new com.prodbuddy.core.tool.ToolStyling("#FFECB3", "#FF6F00", "#FFF8E1", "💻 CodeContext", java.util.Map.of());
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                NAME,
                "Java project context and code search tool",
                Set.of("codecontext.summary", "codecontext.search", "codecontext.p1_context", "codecontext.build_graph_db",
                       "codecontext.refresh_graph_db", "codecontext.context_from_query", "codecontext.incident_report",
                       "codecontext.query_graph", "codecontext.p1_tool_calls", "codecontext.dead_code",
                       "codecontext.change_impact", "codecontext.complexity_report", "codecontext.call_chain")
        );

    }

    @Override
    public boolean supports(ToolRequest request) {
        String intent = request.intent().toLowerCase();
        return intent.contains("code") || intent.contains("context") || intent.contains("repo");
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        seqLog.logSequence("AgentLoopOrchestrator", "CodeContext", "execute", "CodeContext " + request.operation(),
                styling().toMetadata("CodeContext"));
        Path projectPath = projectPath(request.payload());
        String operation = request.operation().toLowerCase();
        ToolResponse response = tryExecuteKnown(operation, projectPath, request.payload(), context);
        if (response != null) {
            seqLog.logSequence("CodeContext", "AgentLoopOrchestrator", "execute", "Completed " + operation,
                    styling().toMetadata("CodeContext"));
            return response;
        }
        return ToolResponse.failure(
                "CODE_OPERATION",
                "supported operations: summary, search, p1_context, build_graph_db, refresh_graph_db, "
                + "context_from_query, incident_report, query_graph, p1_tool_calls, dead_code, "
                + "change_impact, complexity_report, call_chain"
        );
    }

    private ToolResponse tryExecuteKnown(final String operation, final Path projectPath,
                                         final Map<String, Object> payload, final ToolContext context) {
        ToolResponse res = executeGraphOps(operation, projectPath, payload, context);
        if (res != null) {
            return res;
        }
        res = executeAnalysisOps(operation, payload, context);
        if (res != null) {
            return res;
        }
        return switch (operation) {
            case "summary" -> ToolResponse.ok(summaryService.summarize(projectPath));
            case "search" -> ToolResponse.ok(Map.of("matches", search(projectPath, payload, context)));
            case "p1_context" -> ToolResponse.ok(p1Context(projectPath, payload, context));
            case "context_from_query" -> contextFromQuery(projectPath, payload, context);
            case "incident_report" -> incidentReport(projectPath, payload, context);
            default -> null;
        };
    }

    private ToolResponse executeAnalysisOps(final String op, final Map<String, Object> payload, final ToolContext ctx) {
        return switch (op) {
            case "p1_tool_calls" -> ToolResponse.ok(recommendationService.p1ToolCalls(projectPath(payload),
                    dbPath(payload, ctx), String.valueOf(payload.getOrDefault("symptom", "error"))));
            case "dead_code" -> ToolResponse.ok(deadCodeDetector.detect(dbPath(payload, ctx)).toMap());
            case "change_impact" -> changeImpact(payload, ctx);
            case "complexity_report" -> ToolResponse.ok(complexityAnalyzer.heatmap(dbPath(payload, ctx),
                    Integer.parseInt(String.valueOf(payload.getOrDefault("topN", "20")))));
            case "call_chain" -> callChain(payload, ctx);
            default -> null;
        };
    }

    private ToolResponse executeGraphOps(final String operation, final Path projectPath,
                                         final Map<String, Object> payload, final ToolContext context) {
        return switch (operation) {
            case "build_graph_db" -> ToolResponse.ok(buildGraphDb(projectPath, payload, context));
            case "refresh_graph_db" -> ToolResponse.ok(refreshGraphDb(projectPath, payload, context));
            case "query_graph" -> ToolResponse.ok(queryGraph(payload, context));
            default -> null;
        };
    }

    private Path projectPath(final Map<String, Object> payload) {
        String projectPath = String.valueOf(payload.getOrDefault("projectPath", ""));
        if (projectPath.isBlank()) {
            return Path.of(".");
        }
        return Path.of(projectPath);
    }

    private List<Map<String, Object>> search(final Path projectPath,
                                            final Map<String, Object> payload,
                                            final ToolContext context) {
        String query = String.valueOf(payload.getOrDefault("query", ""));
        int max = Integer.parseInt(context.envOrDefault("CODE_CONTEXT_MAX_RESULTS", "20"));
        return searchService.search(projectPath, query, max);
    }

    private Map<String, Object> buildGraphDb(final Path projectPath,
                                             final Map<String, Object> payload,
                                             final ToolContext context) {
        JavaGraphSnapshot snapshot = graphExtractor.extract(projectPath);
        Path dbPath = dbPath(payload, context);
        return graphDbService.build(dbPath, snapshot);
    }

    private Map<String, Object> refreshGraphDb(final Path projectPath,
                                               final Map<String, Object> payload,
                                               final ToolContext context) {
        JavaGraphSnapshot snapshot = graphExtractor.extract(projectPath);
        Path dbPath = dbPath(payload, context);
            String fingerprint = new JavaCodeFingerprinter().fingerprint(projectPath);
        boolean forceRefresh = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("forceRefresh", "false")));
        return graphDbService.refresh(dbPath, snapshot, fingerprint, forceRefresh);
    }

    private Map<String, Object> queryGraph(final Map<String, Object> payload,
                                           final ToolContext context) {
        Path dbPath = dbPath(payload, context);
        String sql = String.valueOf(payload.getOrDefault("sql", "SELECT * FROM ClassNode LIMIT 20"));
        int maxRows = Integer.parseInt(context.envOrDefault("CODE_CONTEXT_MAX_RESULTS", "20"));
        return graphDbService.query(dbPath, sql, maxRows);
    }

    private ToolResponse contextFromQuery(final Path projectPath,
                                         final Map<String, Object> payload,
                                         final ToolContext context) {
        String query = String.valueOf(payload.getOrDefault("query", "")).trim();
        if (query.isBlank()) {
            return ToolResponse.failure("MISSING_QUERY", "query must be provided");
        }
        int max = Integer.parseInt(context.envOrDefault("CODE_CONTEXT_MAX_RESULTS", "20"));
        return ToolResponse.ok(diagnosticService.analyze(projectPath, dbPath(payload, context), query, max,
                new CodeContextRetrievalFacade(searchService, graphDbService), recommendationService));
    }

    private ToolResponse incidentReport(final Path projectPath,
                                       final Map<String, Object> payload,
                                       final ToolContext context) {
        String query = String.valueOf(payload.getOrDefault("query", "")).trim();
        if (query.isBlank()) {
            return ToolResponse.failure("MISSING_QUERY", "query must be provided");
        }
        int max = Integer.parseInt(context.envOrDefault("CODE_CONTEXT_MAX_RESULTS", "20"));
        return ToolResponse.ok(diagnosticService.buildReport(projectPath, dbPath(payload, context), query, max,
                new CodeContextRetrievalFacade(searchService, graphDbService), recommendationService));
    }

    private Map<String, Object> p1Context(final Path projectPath,
                                          final Map<String, Object> payload,
                                          final ToolContext context) {
        String symptom = String.valueOf(payload.getOrDefault("symptom", "error"));
        List<Map<String, Object>> matches = searchService.search(projectPath, symptom, 20);
        Path db = dbPath(payload, context);
        return Map.of(
                "project", summaryService.summarize(projectPath),
                "codeMatches", matches,
                "recommendedQueries", recommendationService.recommendedQueries(symptom),
                "toolCalls", recommendationService.p1ToolCalls(projectPath, db, symptom)
        );
    }


    private Path dbPath(final Map<String, Object> payload, final ToolContext context) {
        String dbPath = String.valueOf(payload.getOrDefault("dbPath", context.envOrDefault("CODE_CONTEXT_DB_PATH", ".prodbuddy/codegraph")));
        return Path.of(dbPath);
    }

    private ToolResponse changeImpact(final Map<String, Object> payload,
                                     final ToolContext context) {
        String target = String.valueOf(payload.getOrDefault("className", "")).trim();
        if (target.isBlank()) {
            return ToolResponse.failure("MISSING_CLASS", "className is required");
        }
        int depth = Integer.parseInt(String.valueOf(payload.getOrDefault("maxDepth", "3")));
        Path dbPath = dbPath(payload, context);
        return ToolResponse.ok(changeImpactAnalyzer.analyze(dbPath, target, depth).toMap());
    }

    private ToolResponse callChain(final Map<String, Object> payload,
                                  final ToolContext context) {
        String startId = String.valueOf(payload.getOrDefault("startMethodId", ""));
        if (startId.isBlank()) {
            return ToolResponse.failure("MISSING_START_ID", "startMethodId is required");
        }
        int depth = Integer.parseInt(String.valueOf(payload.getOrDefault("maxDepth", "3")));
        String dir = String.valueOf(payload.getOrDefault("direction", "DOWN"));
        Path dbPath = dbPath(payload, context);
        List<Map<String, Object>> analysis = callChainService.analyze(dbPath, startId, depth, dir);
        return ToolResponse.ok(Map.of(
                "startMethodId", startId,
                "direction", dir,
                "maxDepth", depth,
                "chainSize", analysis.size(),
                "analysis", analysis
        ));
    }
}
