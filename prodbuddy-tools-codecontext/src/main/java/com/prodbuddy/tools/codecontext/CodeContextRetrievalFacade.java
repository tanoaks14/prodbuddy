package com.prodbuddy.tools.codecontext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodeContextRetrievalFacade {

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[A-Z][a-zA-Z0-9_]{2,}");
    private final JavaCodeSearchService searchService;
    private final LocalGraphDbService graphDbService;

    public CodeContextRetrievalFacade(JavaCodeSearchService searchService, LocalGraphDbService graphDbService) {
        this.searchService = searchService;
        this.graphDbService = graphDbService;
    }

    public Map<String, Object> retrieve(Path projectPath, Path dbPath, String query, int maxResults) {
        List<Map<String, Object>> primaryMatches = primaryMatches(projectPath, query, maxResults);
        List<String> relatedSymbols = relatedSymbols(primaryMatches);
        Map<String, Object> graphContext = graphContext(dbPath, maxResults);
        return Map.of(
                "primaryMatches", primaryMatches,
                "relatedSymbols", relatedSymbols,
                "graphContext", graphContext
        );
    }

    private List<Map<String, Object>> primaryMatches(Path projectPath, String query, int maxResults) {
        List<Map<String, Object>> direct = searchService.search(projectPath, query, maxResults);
        if (!direct.isEmpty()) {
            return direct;
        }
        return fallbackTokenMatches(projectPath, query, maxResults);
    }

    private List<Map<String, Object>> fallbackTokenMatches(Path projectPath, String query, int maxResults) {
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> combined = new ArrayList<>();
        String[] tokens = query.toLowerCase().split("\\W+");
        for (String token : tokens) {
            if (token.length() < 4) {
                continue;
            }
            List<Map<String, Object>> hits = searchService.search(projectPath, token, maxResults);
            for (Map<String, Object> hit : hits) {
                String key = hit.getOrDefault("file", "") + ":" + hit.getOrDefault("line", 0);
                if (seen.add(key)) {
                    combined.add(hit);
                }
                if (combined.size() >= maxResults) {
                    return combined;
                }
            }
        }
        return combined;
    }

    private List<String> relatedSymbols(List<Map<String, Object>> matches) {
        Set<String> symbols = new LinkedHashSet<>();
        for (Map<String, Object> match : matches) {
            String snippet = String.valueOf(match.getOrDefault("snippet", ""));
            Matcher matcher = SYMBOL_PATTERN.matcher(snippet);
            while (matcher.find() && symbols.size() < 20) {
                symbols.add(matcher.group());
            }
        }
        return new ArrayList<>(symbols);
    }

    private Map<String, Object> graphContext(Path dbPath, int maxResults) {
        if (!hasDbArtifacts(dbPath)) {
            return Map.of("available", false, "reason", "graph_db_missing");
        }
        String sql = "SELECT name, COUNT(*) AS usageCount FROM MethodNode GROUP BY name ORDER BY usageCount DESC";
        try {
            Map<String, Object> queryResult = graphDbService.query(dbPath, sql, Math.min(maxResults, 10));
            return Map.of("available", true, "summary", queryResult.get("rows"));
        } catch (IllegalStateException exception) {
            return Map.of("available", false, "reason", "graph_query_failed");
        }
    }

    private boolean hasDbArtifacts(Path dbPath) {
        Path mv = Path.of(dbPath.toString() + ".mv.db");
        Path h2 = Path.of(dbPath.toString() + ".h2.db");
        return Files.exists(mv) || Files.exists(h2);
    }
}
