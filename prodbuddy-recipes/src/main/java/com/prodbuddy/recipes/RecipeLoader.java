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
            } else if (currentStepName != null && !line.isBlank()) {
                // Ignore comment lines starting with # (but not ## which is a step)
                if (line.trim().startsWith("#") && !line.startsWith("##")) {
                    continue;
                }
                currentParams.add(line);
            }
        }
        if (currentStepName != null) {
            steps.add(buildStep(currentStepName, currentParams));
        }
        return steps;
    }

    private RecipeStep buildStep(String name, List<String> paramLines) {
        Map<String, Object> params = new LinkedHashMap<>();
        java.util.Set<String> blockKeys = new java.util.HashSet<>();
        String currentKey = null;

        for (String line : paramLines) {
            currentKey = processStepLine(line, params, currentKey, blockKeys);
        }

        String tool = nvl((String) params.remove("tool"));
        String op = nvl((String) params.remove("operation"));
        String cond = nvl((String) params.remove("condition"));
        String foreach = nvl((String) params.remove("foreach"));
        String as = nvl((String) params.remove("as"));
        boolean stopOnFailure = Boolean.parseBoolean(nvl((String) params.remove("stopOnFailure")));

        List<RecipeStep> nestedSteps = List.of();
        if (params.containsKey("steps")) {
            nestedSteps = parseNestedSteps(params.remove("steps"));
        }

        return new RecipeStep(name, tool, op, cond, foreach, as, stopOnFailure, nestedSteps, params);
    }

    private List<RecipeStep> parseNestedSteps(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        List<RecipeStep> nested = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> stepMap) {
                nested.add(convertMapToStep((Map<String, Object>) stepMap));
            }
        }
        return nested;
    }

    private RecipeStep convertMapToStep(Map<String, Object> map) {
        String name = (String) map.getOrDefault("name", "anonymous");
        String tool = (String) map.getOrDefault("tool", "");
        String op = (String) map.getOrDefault("operation", "");
        String cond = (String) map.getOrDefault("condition", "");
        String foreach = (String) map.getOrDefault("foreach", "");
        String as = (String) map.getOrDefault("as", "");
        boolean stopOnFailure = Boolean.parseBoolean(String.valueOf(map.getOrDefault("stopOnFailure", "false")));

        Map<String, Object> params = new LinkedHashMap<>(map);
        params.remove("name");
        params.remove("tool");
        params.remove("operation");
        params.remove("condition");
        params.remove("foreach");
        params.remove("as");
        params.remove("stopOnFailure");

        List<RecipeStep> nested = List.of();
        if (params.containsKey("steps")) {
            nested = parseNestedSteps(params.remove("steps"));
        }

        return new RecipeStep(name, tool, op, cond, foreach, as, stopOnFailure, nested, params);
    }

    private String processStepLine(String line, Map<String, Object> params, String currentKey, java.util.Set<String> blockKeys) {
        boolean isIndented = line.startsWith("  ") || line.startsWith("\t");
        int sep = line.indexOf(':');

        if (sep >= 0 && !isIndented) {
            String key = line.substring(0, sep).trim();
            String val = line.substring(sep + 1).trim();
            val = stripQuotes(val);
            boolean isBlock = "|".equals(val) || ">".equals(val);
            params.put(key, isBlock ? "" : val);
            if (isBlock) blockKeys.add(key);
            return key;
        } else if (currentKey != null) {
            handleIndentedLine(params.get(currentKey), line, params, currentKey, blockKeys.contains(currentKey));
        }
        return currentKey;
    }

    private String stripQuotes(String val) {
        if (val.length() >= 2 && ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'")))) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }

    @SuppressWarnings("unchecked")
    private void handleIndentedLine(Object existing, String line, Map<String, Object> params, String key, boolean isBlock) {
        String stripped = line.stripLeading();
        int subSep = stripped.indexOf(':');

        // Case 1: Start of a nested step in a 'steps' list
        if ("steps".equals(key)) {
            handleNestedList(params, key, stripped);
            return;
        }

        // Case 2: Standard nested key-value pair (Map)
        if (!isBlock && subSep > 0 && !stripped.substring(0, subSep).contains(" ")) {
            handleNestedMap(existing, params, key, stripped, subSep);
            return;
        }

        // Case 3: Block content or plain string
        if (existing instanceof String s) {
            params.put(key, s + stripped + "\n");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleNestedMap(Object existing, Map<String, Object> params, String key, String stripped, int subSep) {
        Map<String, String> subMap;
        if (existing instanceof Map) {
            subMap = (Map<String, String>) existing;
        } else {
            subMap = new LinkedHashMap<>();
            params.put(key, subMap);
        }
        String subKey = stripped.substring(0, subSep).trim();
        String subVal = stripQuotes(stripped.substring(subSep + 1).trim());
        subMap.put(subKey, subVal);
    }

    @SuppressWarnings("unchecked")
    private void handleNestedList(Map<String, Object> params, String key, String line) {
        List<Map<String, Object>> list;
        Object existing = params.get(key);
        if (existing instanceof List) {
            list = (List<Map<String, Object>>) existing;
        } else {
            list = new ArrayList<>();
            params.put(key, list);
        }

        String stripped = line.stripLeading();
        boolean isNewItem = stripped.startsWith("- ");
        String itemContent = isNewItem ? stripped.substring(2).trim() : stripped;

        int sep = itemContent.indexOf(':');
        if (sep > 0) {
            Map<String, Object> lastMap = initListItem(list, isNewItem, itemContent);
            if (lastMap != null) {
                String subKey = itemContent.substring(0, sep).trim();
                String subVal = stripQuotes(itemContent.substring(sep + 1).trim());
                lastMap.put(subKey, subVal);
            }
        }
    }

    private Map<String, Object> initListItem(List<Map<String, Object>> list, boolean isNewItem, String itemContent) {
        if (isNewItem && itemContent.startsWith("name:")) {
            Map<String, Object> newMap = new LinkedHashMap<>();
            list.add(newMap);
            return newMap;
        } else if (!list.isEmpty()) {
            return list.get(list.size() - 1);
        }
        return null;
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
