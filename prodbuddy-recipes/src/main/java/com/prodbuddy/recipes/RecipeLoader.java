package com.prodbuddy.recipes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeLoader {

    private static final String FRONTMATTER_DELIMITER = "---";
    private static final String STEP_HEADING_PREFIX = "## ";
    private static final String KV_SEPARATOR = ": ";

    public RecipeDefinition load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        int bodyStart = findBodyStart(lines);
        Map<String, String> frontmatter = parseFrontmatter(lines);
        List<RecipeStep> steps = parseSteps(lines, bodyStart);
        return buildDefinition(frontmatter, steps, file);
    }

    private int findBodyStart(List<String> lines) {
        if (lines.isEmpty() || !FRONTMATTER_DELIMITER.equals(lines.get(0).trim())) {
            return 0;
        }
        for (int i = 1; i < lines.size(); i++) {
            if (FRONTMATTER_DELIMITER.equals(lines.get(i).trim())) {
                return i + 1;
            }
        }
        return 0;
    }

    private Map<String, String> parseFrontmatter(List<String> lines) {
        Map<String, String> result = new LinkedHashMap<>();
        if (lines.isEmpty() || !FRONTMATTER_DELIMITER.equals(lines.get(0).trim())) {
            return result;
        }
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (FRONTMATTER_DELIMITER.equals(line)) {
                break;
            }
            parseFrontmatterLine(line, result);
        }
        return result;
    }

    private void parseFrontmatterLine(String line, Map<String, String> result) {
        int sep = line.indexOf(KV_SEPARATOR);
        if (sep < 0) {
            return;
        }
        String key = line.substring(0, sep).trim();
        String value = line.substring(sep + KV_SEPARATOR.length()).trim();
        result.put(key, value);
    }

    private List<RecipeStep> parseSteps(List<String> lines, int bodyStart) {
        List<RecipeStep> steps = new ArrayList<>();
        String currentStepName = null;
        List<String> currentParams = new ArrayList<>();
        for (int i = bodyStart; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(STEP_HEADING_PREFIX)) {
                if (currentStepName != null) {
                    steps.add(buildStep(currentStepName, currentParams));
                }
                currentStepName = line.substring(STEP_HEADING_PREFIX.length()).trim();
                currentParams = new ArrayList<>();
            } else if (currentStepName != null && line.contains(KV_SEPARATOR)) {
                currentParams.add(line.trim());
            }
        }
        if (currentStepName != null) {
            steps.add(buildStep(currentStepName, currentParams));
        }
        return steps;
    }

    private RecipeStep buildStep(String name, List<String> paramLines) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String line : paramLines) {
            int sep = line.indexOf(KV_SEPARATOR);
            if (sep < 0) {
                continue;
            }
            String key = line.substring(0, sep).trim();
            String value = line.substring(sep + KV_SEPARATOR.length()).trim();
            params.put(key, value);
        }
        String tool = params.remove("tool");
        String operation = params.remove("operation");
        return new RecipeStep(name, nvl(tool), nvl(operation), params);
    }

    private RecipeDefinition buildDefinition(Map<String, String> frontmatter, List<RecipeStep> steps, Path file) {
        String name = frontmatter.getOrDefault("name", fileBaseName(file));
        String description = frontmatter.getOrDefault("description", "");
        List<String> tags = parseTags(frontmatter.getOrDefault("tags", ""));
        return new RecipeDefinition(name, description, tags, steps);
    }

    private List<String> parseTags(String raw) {
        String stripped = raw.replaceAll("[\\[\\]]", "").trim();
        if (stripped.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String tag : stripped.split(",")) {
            String trimmed = tag.trim();
            if (!trimmed.isBlank()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    private String fileBaseName(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
