package com.prodbuddy.tools.codecontext;

import java.util.List;
import java.util.Map;

public record CodeQueryIntent(
        String category,
        String normalizedQuery,
        List<String> entities,
        List<String> expandedTerms,
        double confidence
        ) {

    public Map<String, Object> toMap() {
        return Map.of(
                "category", category,
                "normalizedQuery", normalizedQuery,
                "entities", entities,
                "expandedTerms", expandedTerms,
                "confidence", confidence
        );
    }
}
