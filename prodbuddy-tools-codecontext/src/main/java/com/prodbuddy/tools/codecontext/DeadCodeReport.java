package com.prodbuddy.tools.codecontext;

import java.util.List;
import java.util.Map;

/** Immutable report of methods detected as unreachable from any call graph edge. */
public record DeadCodeReport(
        String dbPath,
        List<Map<String, Object>> candidates,
        int totalCandidates,
        int totalMethods
) {

    public Map<String, Object> toMap() {
        return Map.of(
                "dbPath", dbPath,
                "totalMethods", totalMethods,
                "totalDeadCandidates", totalCandidates,
                "deadCodeCandidates", candidates
        );
    }
}
