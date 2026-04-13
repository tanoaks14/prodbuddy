package com.prodbuddy.tools.codecontext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

public final class JavaProjectSummaryService {

    public Map<String, Object> summarize(Path rootPath) {
        if (!Files.exists(rootPath)) {
            return Map.of("exists", false, "path", rootPath.toString());
        }

        try (Stream<Path> files = Files.walk(rootPath)) {
            long javaFiles = files.filter(this::isJavaFile).count();
            return Map.of("exists", true, "path", rootPath.toString(), "javaFiles", javaFiles);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to summarize project: " + rootPath, exception);
        }
    }

    private boolean isJavaFile(Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
    }
}
