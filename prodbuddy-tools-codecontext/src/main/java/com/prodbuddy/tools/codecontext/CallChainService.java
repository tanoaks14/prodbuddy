package com.prodbuddy.tools.codecontext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service to traverse call chains and extract exact source code snippets.
 */
public final class CallChainService {

    private final LocalGraphQueries queries;

    public CallChainService(final LocalGraphQueries queries) {
        this.queries = queries;
    }

    /**
     * Traverses the call graph up or down and extracts source code.
     */
    public List<Map<String, Object>> analyze(final Path dbPath, final String startId,
                                            final int maxDepth, final String direction) {
        Set<String> visited = new LinkedHashSet<>();
        List<Map<String, Object>> chain = new ArrayList<>();
        traverse(dbPath, startId, 0, maxDepth, direction.toUpperCase(), visited, chain);
        return chain;
    }

    private void traverse(final Path db, final String id, final int depth,
                         final int max, final String dir, final Set<String> vis,
                         final List<Map<String, Object>> results) {
        if (depth > max || vis.contains(id)) {
            return;
        }
        vis.add(id);
        GraphMethodNode node = queries.getMethodNode(db, id);
        if (node == null) {
            return;
        }

        Map<String, Object> context = buildNodeContext(node, depth);
        results.add(context);

        List<String> nextIds = "UP".equals(dir)
                ? queries.findCallersByMethodId(db, id)
                : queries.findCalleesByMethodId(db, id);
        for (String nextId : nextIds) {
            traverse(db, nextId, depth + 1, max, dir, vis, results);
        }
    }

    private Map<String, Object> buildNodeContext(final GraphMethodNode node,
                                                  final int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", node.id());
        map.put("name", node.name());
        map.put("classFqn", node.classFqn());
        map.put("depth", depth);
        map.put("filePath", node.filePath());
        map.put("lines", node.startLine() + "-" + node.endLine());
        map.put("code", extractCode(node.filePath(), node.startLine(), node.endLine()));
        return map;
    }

    private String extractCode(final String filePath, final int start, final int end) {
        if (start <= 0 || end <= 0 || start > end) {
            return "[error: invalid line range " + start + ":" + end + "]";
        }
        try {
            List<String> lines = Files.readAllLines(Path.of(filePath));
            if (start > lines.size()) {
                return "[error: start line " + start + " exceeds file length " + lines.size() + "]";
            }
            int actualEnd = Math.min(end, lines.size());
            return lines.subList(start - 1, actualEnd).stream().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "[error reading " + filePath + ": " + e.getMessage() + "]";
        }
    }
}
