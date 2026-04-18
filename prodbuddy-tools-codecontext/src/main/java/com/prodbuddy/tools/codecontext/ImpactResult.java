package com.prodbuddy.tools.codecontext;

import java.util.List;
import java.util.Map;

/** Immutable result of a change impact BFS traversal through the call graph. */
public record ImpactResult(
        String target,
        int maxDepth,
        int totalImpacted,
        List<Map<String, Object>> impactedByDepth
) {

    public Map<String, Object> toMap() {
        return Map.of(
                "target", target,
                "maxDepth", maxDepth,
                "totalImpacted", totalImpacted,
                "impactedByDepth", impactedByDepth
        );
    }
}
