package com.prodbuddy.tools.codecontext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Reads ClassMetrics from H2 and produces a sorted complexity heatmap report. */
public final class ComplexityAnalyzer {

    private static final int DEFAULT_TOP_N = 20;
    private final LocalGraphQueries graphQueries;

    public ComplexityAnalyzer(LocalGraphQueries graphQueries) {
        this.graphQueries = graphQueries;
    }

    /** Returns the top N most complex classes, sorted by composite score descending. */
    public Map<String, Object> heatmap(Path dbPath, int topN) {
        List<Map<String, Object>> rows = graphQueries.queryComplexityHeatmap(dbPath, topN);
        int highRisk = countByRisk(rows, 20);
        int mediumRisk = countByRisk(rows, 10) - highRisk;
        return Map.of(
                "dbPath", dbPath.toAbsolutePath().toString(),
                "topN", topN,
                "highRiskCount", highRisk,
                "mediumRiskCount", mediumRisk,
                "classes", rows
        );
    }

    /** Convenience with default top N. */

    private int countByRisk(List<Map<String, Object>> rows, int threshold) {
        return (int) rows.stream()
                .filter(r -> scoreOf(r) >= threshold)
                .count();
    }

    private int scoreOf(Map<String, Object> row) {
        Object score = row.get("COMPOSITESCORE");
        if (score == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(score));
    }
}
