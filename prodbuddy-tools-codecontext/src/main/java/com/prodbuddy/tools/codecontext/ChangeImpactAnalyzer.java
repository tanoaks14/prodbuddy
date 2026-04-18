package com.prodbuddy.tools.codecontext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Performs a BFS upstream traversal of the call graph to determine
 * which callers are transitively affected by changing a given class or method.
 */
public final class ChangeImpactAnalyzer {

    private static final int DEFAULT_MAX_DEPTH = 3;

    private final LocalGraphQueries graphQueries;

    public ChangeImpactAnalyzer(LocalGraphQueries graphQueries) {
        this.graphQueries = graphQueries;
    }

    /** Analyze the blast radius of changing the given class/method name. */
    public ImpactResult analyze(Path dbPath, String target, int maxDepth) {
        Set<String> visited = new HashSet<>();
        List<Map<String, Object>> impactedByDepth = new ArrayList<>();
        Set<String> frontier = seedFrontier(dbPath, target);
        for (int depth = 1; depth <= maxDepth && !frontier.isEmpty(); depth++) {
            frontier.removeAll(visited);
            if (frontier.isEmpty()) {
                break;
            }
            visited.addAll(frontier);
            impactedByDepth.add(Map.of("depth", depth, "callers", new ArrayList<>(frontier)));
            frontier = expandFrontier(dbPath, frontier);
        }
        return new ImpactResult(target, maxDepth, visited.size(), impactedByDepth);
    }

    /** Convenience with default depth. */
    public ImpactResult analyze(Path dbPath, String target) {
        return analyze(dbPath, target, DEFAULT_MAX_DEPTH);
    }

    private Set<String> seedFrontier(Path dbPath, String target) {
        List<String> direct = graphQueries.findCallersByClass(dbPath, target);
        return new HashSet<>(direct);
    }

    private Set<String> expandFrontier(Path dbPath, Set<String> frontier) {
        Set<String> next = new HashSet<>();
        for (String callee : frontier) {
            next.addAll(graphQueries.findCallersByMethodId(dbPath, callee));
        }
        return next;
    }
}
