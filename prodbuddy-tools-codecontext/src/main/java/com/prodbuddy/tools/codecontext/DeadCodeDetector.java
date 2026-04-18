package com.prodbuddy.tools.codecontext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Detects methods with no inbound call edges in the graph database.
 * Uses the H2 graph already built by LocalGraphDbService — no re-parsing needed.
 */
public final class DeadCodeDetector {

    private static final int DEFAULT_TOTAL_LIMIT = 5000;

    private final LocalGraphQueries graphQueries;

    public DeadCodeDetector(LocalGraphQueries graphQueries) {
        this.graphQueries = graphQueries;
    }

    /** Returns methods never referenced as a call target, excluding constructors and main. */
    public DeadCodeReport detect(Path dbPath, int maxCandidates) {
        int total = graphQueries.countMethods(dbPath);
        List<Map<String, Object>> candidates = graphQueries.detectDeadCode(dbPath, maxCandidates);
        return new DeadCodeReport(
                dbPath.toAbsolutePath().toString(),
                candidates,
                candidates.size(),
                total
        );
    }

    /** Convenience overload using default candidate limit. */
    public DeadCodeReport detect(Path dbPath) {
        return detect(dbPath, DEFAULT_TOTAL_LIMIT);
    }
}
