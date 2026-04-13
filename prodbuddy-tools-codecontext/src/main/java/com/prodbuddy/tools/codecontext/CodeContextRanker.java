package com.prodbuddy.tools.codecontext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CodeContextRanker {

    public List<Map<String, Object>> rank(List<Map<String, Object>> matches, CodeQueryIntent intent, int topN) {
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> match : matches) {
            scored.add(score(match, intent));
        }
        scored.sort(Comparator.comparingDouble(this::scoreOf).reversed());
        if (scored.size() <= topN) {
            return scored;
        }
        return scored.subList(0, topN);
    }

    private Map<String, Object> score(Map<String, Object> match, CodeQueryIntent intent) {
        String snippet = String.valueOf(match.getOrDefault("snippet", "")).toLowerCase(Locale.ROOT);
        double score = 0.2;
        List<String> reasons = new ArrayList<>();
        if (snippet.contains(intent.normalizedQuery())) {
            score += 0.5;
            reasons.add("exact_query_match");
        }
        score += expandedTermBoost(snippet, intent.expandedTerms(), reasons);
        score += entityBoost(snippet, intent.entities(), reasons);
        return Map.of(
                "file", match.getOrDefault("file", ""),
                "line", match.getOrDefault("line", 0),
                "snippet", match.getOrDefault("snippet", ""),
                "score", Math.min(score, 1.0),
                "reasons", reasons
        );
    }

    private double expandedTermBoost(String snippet, List<String> terms, List<String> reasons) {
        double boost = 0.0;
        for (String term : terms) {
            if (snippet.contains(term.toLowerCase(Locale.ROOT))) {
                boost += 0.15;
                reasons.add("expanded_term:" + term);
            }
        }
        return boost;
    }

    private double entityBoost(String snippet, List<String> entities, List<String> reasons) {
        double boost = 0.0;
        for (String entity : entities) {
            if (snippet.contains(entity.toLowerCase(Locale.ROOT))) {
                boost += 0.1;
                reasons.add("entity:" + entity);
            }
        }
        return boost;
    }

    private double scoreOf(Map<String, Object> value) {
        return ((Number) value.getOrDefault("score", 0.0)).doubleValue();
    }
}
