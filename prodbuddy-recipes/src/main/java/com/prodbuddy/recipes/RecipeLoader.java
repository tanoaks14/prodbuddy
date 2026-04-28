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
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    public RecipeDefinition load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        int bodyStart = findBodyStart(lines);
        Map<String, String> frontmatter = parseFrontmatter(lines);
        List<RecipeStep> steps = parseSteps(lines, bodyStart, file);
        List<RecipeStep> resolvedSteps = resolveInclusions(steps, file);
        return buildDefinition(frontmatter, resolvedSteps, file);
    }
    private int findBodyStart(List<String> lines) {
        if (lines.isEmpty() || !FRONTMATTER_DELIMITER.equals(lines.get(0).trim())) return 0;
        for (int i = 1; i < lines.size(); i++) {
            if (FRONTMATTER_DELIMITER.equals(lines.get(i).trim())) return i + 1;
        }
        return 0;
    }
    private Map<String, String> parseFrontmatter(List<String> lines) {
        Map<String, String> result = new LinkedHashMap<>();
        if (lines.isEmpty() || !FRONTMATTER_DELIMITER.equals(lines.get(0).trim())) return result;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (FRONTMATTER_DELIMITER.equals(line)) break;
            parseFrontmatterLine(line, result);
        }
        return result;
    }
    private void parseFrontmatterLine(String line, Map<String, String> result) {
        int sep = line.indexOf(KV_SEPARATOR);
        if (sep < 0) return;
        result.put(line.substring(0, sep).trim(), line.substring(sep + KV_SEPARATOR.length()).trim());
    }
    private List<RecipeStep> parseSteps(List<String> lines, int bodyStart, Path recipeFile) {
        List<RecipeStep> steps = new ArrayList<>();
        String currentStepName = null;
        List<String> currentParams = new ArrayList<>();
        for (int i = bodyStart; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(STEP_HEADING_PREFIX)) {
                if (currentStepName != null) steps.add(buildStep(currentStepName, currentParams, recipeFile));
                currentStepName = line.substring(STEP_HEADING_PREFIX.length()).trim();
                currentParams = new ArrayList<>();
            } else if (currentStepName != null) {
                if (line.trim().startsWith("#") && !line.trim().startsWith("##")) continue;
                currentParams.add(line);
            }
        }
        if (currentStepName != null) steps.add(buildStep(currentStepName, currentParams, recipeFile));
        return steps;
    }
    private RecipeStep buildStep(String name, List<String> paramLines, Path recipeFile) {
        lastKeys.clear();
        Map<String, Object> params = new LinkedHashMap<>();
        java.util.Set<String> blockKeys = new java.util.HashSet<>();
        java.util.Map<String, Integer> blockIndents = new java.util.HashMap<>();
        String currentKey = null;
        for (String line : paramLines) {
            currentKey = processStepLine(line, params, currentKey, blockKeys, blockIndents, recipeFile);
        }
        String tool = nvl(params.remove("tool")).trim();
        String op = nvl(params.remove("operation")).trim();
        String cond = nvl(params.remove("condition")).trim();
        String foreach = nvl(params.remove("foreach")).trim();
        String as = nvl(params.remove("as")).trim();
        boolean stopOnFailure = Boolean.parseBoolean(nvl(params.remove("stopOnFailure")));
        List<RecipeStep> nestedSteps = params.containsKey("steps") ? parseNestedSteps(params.remove("steps")) : List.of();
        return new RecipeStep(name, tool, op, cond, foreach, as, stopOnFailure, nestedSteps, params);
    }
    private List<RecipeStep> parseNestedSteps(Object raw) {
        if (!(raw instanceof List<?> rawList)) return List.of();
        List<RecipeStep> nested = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> stepMap) nested.add(convertMapToStep((Map<String, Object>) stepMap));
        }
        return nested;
    }
    private RecipeStep convertMapToStep(Map<String, Object> map) {
        String name = (String) map.getOrDefault("name", "anonymous");
        String tool = (String) map.getOrDefault("tool", ""), op = (String) map.getOrDefault("operation", "");
        String cond = (String) map.getOrDefault("condition", ""), fe = (String) map.getOrDefault("foreach", "");
        String as = (String) map.getOrDefault("as", "");
        boolean stop = Boolean.parseBoolean(String.valueOf(map.getOrDefault("stopOnFailure", "false")));
        Map<String, Object> p = new LinkedHashMap<>(map);
        List.of("name", "tool", "operation", "condition", "foreach", "as", "stopOnFailure").forEach(p::remove);
        List<RecipeStep> nested = p.containsKey("steps") ? parseNestedSteps(p.remove("steps")) : List.of();
        return new RecipeStep(name, tool, op, cond, fe, as, stop, nested, p);
    }
    private String processStepLine(String line, Map<String, Object> params, String currentKey,
                                   java.util.Set<String> blockKeys, java.util.Map<String, Integer> blockIndents,
                                   Path recipeFile) {
        if (line.trim().isEmpty()) return handleEmptyLine(currentKey, blockKeys, blockIndents, params, recipeFile);
        boolean isIndented = line.startsWith(" ") || line.startsWith("\t");
        int sep = line.indexOf(':');
        if (sep >= 0 && !isIndented) {
            String key = line.substring(0, sep).trim(), val = line.substring(sep + 1).trim();
            return handleNewKey(key, val, params, blockKeys, recipeFile);
        } else if (currentKey != null) {
            handleIndentedLine(params.get(currentKey), line, params, currentKey, blockKeys.contains(currentKey), blockIndents, recipeFile);
        }
        return currentKey;
    }
    private String handleEmptyLine(String key, java.util.Set<String> blocks, java.util.Map<String, Integer> blockIndents, Map<String, Object> params, Path file) {
        if (key != null && blocks.contains(key)) handleIndentedLine(params.get(key), "", params, key, true, blockIndents, file);
        return key;
    }
    private String handleNewKey(final String key, final String val, final Map<String, Object> params, final java.util.Set<String> blocks, final Path file) {
        Object v = val.startsWith("@file:") ? readFileContent(file, val.substring(6)) : parseValue(val);
        boolean isBlock = "|".equals(v) || ">".equals(v);
        params.put(key, isBlock ? "" : v);
        if (isBlock) blocks.add(key);
        return key;
    }
    private String readFileContent(Path currentFile, String rawPath) {
        try {
            Path path = currentFile.getParent().resolve(rawPath.trim());
            if (!Files.exists(path)) path = Path.of(rawPath.trim());
            return Files.readString(path);
        } catch (IOException e) {
            return "FILE_NOT_FOUND: " + rawPath;
        }
    }
    private String stripQuotes(String val) {
        if (val.length() >= 2 && ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'")))) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }
    private Object parseValue(String val) {
        if (val == null || val.isEmpty()) return "";
        String v = val.trim();
        String unquoted = stripQuotes(v);
        if (unquoted.length() != v.length()) return unquoted;
        if (v.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (v.equalsIgnoreCase("false")) return Boolean.FALSE;
        return parseNumericOrComplex(v);
    }
    private Object parseNumericOrComplex(String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            if (v.startsWith("[") || v.startsWith("{")) return parseJson(v);
            return v;
        }
    }
    private Object parseJson(String v) {
        try {
            return MAPPER.readValue(v, Object.class);
        } catch (Exception ignored) {
            return v;
        }
    }
    private final Map<Integer, String> lastKeys = new java.util.HashMap<>();
    @SuppressWarnings("unchecked")
    private void handleIndentedLine(Object existing, String line, Map<String, Object> params,
                                    String key, boolean isBlock, java.util.Map<String, Integer> blockIndents,
                                    Path recipeFile) {
        if (isBlock) {
            handleBlockLine(existing, line, params, key, recipeFile);
            return;
        }
        String stripped = line.stripLeading();
        int indent = line.indexOf(stripped), subSep = stripped.indexOf(':');
        if (stripped.startsWith("- ") || "steps".equals(key) || existing instanceof List) {
            handleGeneralList(params, key, stripped);
            return;
        }
        if (subSep > 0 && !stripped.substring(0, subSep).contains(" ")) {
            handleNestedKey(stripped, subSep, params, key, indent);
            return;
        }
        // Detect block base indentation on first line
        if (!blockIndents.containsKey(key)) {
            blockIndents.put(key, indent);
        }
        int baseIndent = blockIndents.get(key);
        String val = line.substring(Math.min(indent, baseIndent));
        if (existing instanceof String s) params.put(key, s + val + "\n");
    }
    private void handleBlockLine(Object existing, String line, Map<String, Object> params, String key, Path recipeFile) {
        if (line.trim().isEmpty()) {
            if (existing instanceof String s) params.put(key, s + "\n");
            return;
        }
        // Detect and remove indentation (assumed at least 2)
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        // If we have at least 2 spaces, remove them. Otherwise remove all leading spaces.
        String val = line.substring(Math.min(i, 2));
        String trimmedVal = val.trim();
        if (trimmedVal.startsWith("@file:")) {
            val = readFileContent(recipeFile, trimmedVal.substring(6));
        }
        if (existing instanceof String s) params.put(key, s + val + "\n");
    }
    private void handleNestedKey(final String stripped, final int subSep,
                                final Map<String, Object> params,
                                final String key, final int indent) {
        String subKey = stripped.substring(0, subSep).trim();
        String subValStr = stripped.substring(subSep + 1).trim();
        Object subVal = parseValue(subValStr);
        Map<String, Object> parent = getOrCreateParent(params, key, indent);
        parent.put(subKey, subVal);
        lastKeys.put(indent, subKey);
    }
    @SuppressWarnings("unchecked")
    private void handleGeneralList(Map<String, Object> params, String key, String line) {
        Object existing = params.get(key);
        List<Object> list = (existing instanceof List) ? (List<Object>) existing : new ArrayList<>();
        if (!(existing instanceof List)) params.put(key, list);
        String s = line.stripLeading();
        boolean newItem = s.startsWith("- ");
        String content = newItem ? s.substring(2).trim() : s;
        if (content.contains(":") && !content.startsWith("\"")) {
            int sep = content.indexOf(':');
            Map<String, Object> lastMap = (newItem || list.isEmpty()) ? new LinkedHashMap<>() : (Map<String, Object>) list.get(list.size() - 1);
            if (newItem || list.isEmpty()) list.add(lastMap);
            lastMap.put(content.substring(0, sep).trim(), parseValue(content.substring(sep + 1).trim()));
        } else {
            list.add(parseValue(content));
        }
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateParent(Map<String, Object> params, String key, int indent) {
        Object root = params.get(key);
        if (!(root instanceof Map)) {
            root = new LinkedHashMap<String, Object>();
            params.put(key, root);
        }
        Map<String, Object> current = (Map<String, Object>) root;
        for (int i = 2; i < indent; i += 2) {
            String parentKey = lastKeys.get(i);
            if (parentKey == null) break;
            Object next = current.get(parentKey);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(parentKey, next);
            }
            current = (Map<String, Object>) next;
        }
        return current;
    }
    private List<RecipeStep> resolveInclusions(List<RecipeStep> steps, Path file) throws IOException {
        List<RecipeStep> resolved = new ArrayList<>();
        for (RecipeStep step : steps) {
            if ("recipe".equals(step.tool()) && "include".equals(step.operation())) {
                String path = (String) step.rawParams().get("path");
                if (path != null) {
                    Path includePath = file.getParent().resolve(path.trim());
                    if (Files.exists(includePath)) resolved.addAll(load(includePath).steps());
                }
            } else resolved.add(step);
        }
        return resolved;
    }
    private RecipeDefinition buildDefinition(Map<String, String> frontmatter, List<RecipeStep> steps, Path file) {
        String name = frontmatter.getOrDefault("name", fileBaseName(file)), desc = frontmatter.getOrDefault("description", "");
        List<String> tags = parseTags(frontmatter.getOrDefault("tags", ""));
        boolean analysis = Boolean.parseBoolean(frontmatter.getOrDefault("analysis", "false"));
        return new RecipeDefinition(name, desc, tags, analysis, steps);
    }
    private List<String> parseTags(String raw) {
        String s = raw.replaceAll("[\\[\\]]", "").trim();
        if (s.isBlank()) return List.of();
        List<String> tags = new ArrayList<>();
        for (String t : s.split(",")) if (!t.trim().isBlank()) tags.add(t.trim());
        return tags;
    }
    private String fileBaseName(Path f) {
        String n = f.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
    private String nvl(Object v) {
        return v != null ? String.valueOf(v) : "";
    }
}
