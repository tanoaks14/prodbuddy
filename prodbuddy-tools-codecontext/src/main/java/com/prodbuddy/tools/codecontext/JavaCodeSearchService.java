package com.prodbuddy.tools.codecontext;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class JavaCodeSearchService {

    public List<Map<String, Object>> search(Path rootPath, String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        try (Stream<Path> files = Files.walk(rootPath)) {
            files.filter(this::isJavaFile)
                    .forEach(path -> searchFile(path, query, maxResults, matches));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to search code in: " + rootPath, exception);
        }
        return matches;
    }

    private boolean isJavaFile(Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
    }

    private void searchFile(Path filePath, String query, int maxResults, List<Map<String, Object>> matches) {
        if (matches.size() >= maxResults) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null && matches.size() < maxResults) {
                lineNo++;
                if (line.toLowerCase().contains(query.toLowerCase())) {
                    matches.add(match(filePath, lineNo, line));
                }
            }
        } catch (IOException exception) {
            // Ignore unreadable files and continue search.
        }
    }

    private Map<String, Object> match(Path filePath, int lineNo, String line) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("file", filePath.toString());
        item.put("line", lineNo);
        item.put("snippet", line.trim());
        return item;
    }
}
