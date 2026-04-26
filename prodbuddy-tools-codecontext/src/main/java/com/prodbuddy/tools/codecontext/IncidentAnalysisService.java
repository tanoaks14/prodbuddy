package com.prodbuddy.tools.codecontext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Service to handle code search query analysis and incident correlation.
 */
public final class IncidentAnalysisService {

    private final CodeQueryIntentParser intentParser = new CodeQueryIntentParser();
    private final CodeContextRanker ranker = new CodeContextRanker();
    private final IncidentCorrelationService correlationService = new IncidentCorrelationService();

    public Map<String, Object> analyze(final Path proj, final Path db, final String query,
                                       final int max, final CodeContextRetrievalFacade facade,
                                       final ToolRecommendationService recommendations) {
        CodeQueryIntent intent = intentParser.parse(query);
        Map<String, Object> retrieval = facade.retrieve(proj, db, query, max * 3);
        List<Map<String, Object>> primary = asMatches(retrieval.get("primaryMatches"));
        List<Map<String, Object>> ranked = ranker.rank(primary, intent, max);
        return Map.of(
                "intent", intent.toMap(),
                "primaryMatches", primary,
                "relatedSymbols", retrieval.getOrDefault("relatedSymbols", List.of()),
                "graphContext", retrieval.getOrDefault("graphContext", Map.of("available", false)),
                "rankedFindings", ranked,
                "explainability", explainability(intent, ranked),
                "nextActions", recommendations.nextActions(proj, db, query)
        );
    }

    public Map<String, Object> report(final Path proj, final Path db, final String query,
                                      final int max, final CodeContextRetrievalFacade facade,
                                      final ToolRecommendationService recommendations) {
        CodeQueryIntent intent = intentParser.parse(query);
        Map<String, Object> bundle = analyze(proj, db, query, max, facade, recommendations);
        return correlationService.build(query, intent, bundle, proj, db);
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
}
