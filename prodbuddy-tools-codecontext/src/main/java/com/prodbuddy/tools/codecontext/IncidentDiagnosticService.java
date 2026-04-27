package com.prodbuddy.tools.codecontext;
 
 import java.nio.file.Path;
 import java.util.List;
 import java.util.Map;
 
 /**
  * Unified service to handle code search query analysis, incident correlation,
  * and recommended diagnostic steps.
  */
 public final class IncidentDiagnosticService {
 
     private final CodeQueryIntentParser intentParser = new CodeQueryIntentParser();
     private final CodeContextRanker ranker = new CodeContextRanker();
     private final CallChainService callChainService;
     private final LocalGitService gitService;
     private final LocalGraphQueries queries;
 
     public IncidentDiagnosticService(final CallChainService callChainService,
                                      final LocalGitService gitService,
                                      final LocalGraphQueries queries) {
         this.callChainService = callChainService;
         this.gitService = gitService;
         this.queries = queries;
     }
 
     /**
      * Analyzes a symptom and retrieves relevant code context.
      */
     public Map<String, Object> analyze(final Path proj, final Path db, final String query,
                                        final int max, final CodeContextRetrievalFacade facade,
                                        final ToolRecommendationService recommendations) {
         CodeQueryIntent intent = intentParser.parse(query);
         Map<String, Object> retrieval = facade.retrieve(proj, db, query, max * 3);
         List<Map<String, Object>> primary = asMatches(retrieval.get("primaryMatches"));
         List<Map<String, Object>> ranked = ranker.rank(primary, intent, max);
 
         // Enrich findings with deep context
         List<Map<String, Object>> enriched = new java.util.ArrayList<>();
         for (Map<String, Object> finding : ranked) {
             Map<String, Object> mutableFinding = new java.util.HashMap<>(finding);
             enrichWithDeepContext(db, mutableFinding);
             enriched.add(mutableFinding);
         }
 
         return Map.of(
                 "intent", intent.toMap(),
                 "primaryMatches", primary,
                 "relatedSymbols", retrieval.getOrDefault("relatedSymbols", List.of()),
                 "graphContext", retrieval.getOrDefault("graphContext", Map.of("available", false)),
                 "rankedFindings", enriched,
                 "explainability", explainability(intent, enriched),
                 "nextActions", recommendations.nextActions(proj, db, query)
         );
     }
 
     private void enrichWithDeepContext(final Path db, final Map<String, Object> finding) {
         String filePath = String.valueOf(finding.get("file"));
         int line = Integer.parseInt(String.valueOf(finding.get("line")));
 
         // 1. Git Risk Signals
         finding.put("gitRisk", gitService.getRiskMetadata(filePath));
 
         // 2. Resolve Method ID from location
         String methodId = queries.findMethodIdByLocation(db, filePath, line);
         if (methodId != null) {
             finding.put("methodId", methodId);
 
             // 3. Call Chain (Upstream)
             finding.put("upstreamChain", callChainService.analyze(db, methodId, 3, "UP"));
 
             // 4. Full Source Retrieval
             GraphMethodNode node = queries.getMethodNode(db, methodId);
             if (node != null) {
                 finding.put("fullSource", extractFullSource(node));
             }
         }
     }
 
     private String extractFullSource(GraphMethodNode node) {
         try {
             List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of(node.filePath()));
             int start = Math.max(1, node.startLine());
             int end = Math.min(lines.size(), node.endLine());
             return String.join("\n", lines.subList(start - 1, end));
         } catch (Exception e) {
             return "[Error reading source: " + e.getMessage() + "]";
         }
     }
 
     /**
      * Generates a full incident report including telemetry queries and correlation rules.
      */
     public Map<String, Object> buildReport(final Path projectPath, final Path dbPath, final String query,
                                            final int max, final CodeContextRetrievalFacade facade,
                                            final ToolRecommendationService recommendations) {
         CodeQueryIntent intent = intentParser.parse(query);
         Map<String, Object> bundle = analyze(projectPath, dbPath, query, max, facade, recommendations);
 
         return Map.of(
                 "query", query,
                 "intent", intent.toMap(),
                 "deterministicStrategy", strategy(),
                 "codeContext", bundle,
                 "telemetryQueries", telemetryQueries(query),
                 "correlationRules", correlationRules(),
                 "recommendedExecutionOrder", executionOrder(projectPath, dbPath, query)
         );
     }
 
     @SuppressWarnings("unchecked")
     private List<Map<String, Object>> asMatches(final Object value) {
         if (!(value instanceof List<?> list)) {
             return List.of();
         }
         return (List<Map<String, Object>>) (List<?>) list;
     }
 
     private Map<String, Object> explainability(final CodeQueryIntent intent,
                                                final List<Map<String, Object>> ranked) {
         return Map.of(
                 "confidence", intent.confidence(),
                 "usedSignals", List.of("text_match", "expanded_terms", "entity_hits"),
                 "topFindingCount", ranked.size(),
                 "queryCategory", intent.category()
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
