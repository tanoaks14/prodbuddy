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
    private List<RecipeStep> parseSteps(List<String> lines, int bodyStart) {
        List<RecipeStep> steps = new ArrayList<>();
        String currentStepName = null;
        List<String> currentParams = new ArrayList<>();
        for (int i = bodyStart; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(STEP_HEADING_PREFIX)) {
                if (currentStepName != null) steps.add(buildStep(currentStepName, currentParams));
                currentStepName = line.substring(STEP_HEADING_PREFIX.length()).trim();
                currentParams = new ArrayList<>();
            } else if (currentStepName != null) {
                if (line.trim().startsWith("#") && !line.trim().startsWith("##")) continue;
                currentParams.add(line);
            }
        }
        if (currentStepName != null) steps.add(buildStep(currentStepName, currentParams));
        return steps;
    }
    private RecipeStep buildStep(String name, List<String> paramLines) {
        lastKeys.clear();
        Map<String, Object> params = new LinkedHashMap<>();
        java.util.Set<String> blockKeys = new java.util.HashSet<>();
        String currentKey = null;
        for (String line : paramLines) currentKey = processStepLine(line, params, currentKey, blockKeys);
        String tool = nvl((String) params.remove("tool")).trim();
        String op = nvl((String) params.remove("operation")).trim();
        String cond = nvl((String) params.remove("condition")).trim();
        String foreach = nvl((String) params.remove("foreach")).trim();
        String as = nvl((String) params.remove("as")).trim();
        boolean stopOnFailure = Boolean.parseBoolean(nvl((String) params.remove("stopOnFailure")));
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
    private String processStepLine(String line, Map<String, Object> params, String currentKey, java.util.Set<String> blockKeys) {
        boolean isIndented = line.startsWith("  ") || line.startsWith("\t");
        int sep = line.indexOf(':');
        if (sep >= 0 && !isIndented) {
            String key = line.substring(0, sep).trim(), val = stripQuotes(line.substring(sep + 1).trim());
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
    private final Map<Integer, String> lastKeys = new java.util.HashMap<>();
    @SuppressWarnings("unchecked")
    private void handleIndentedLine(Object existing, String line, Map<String, Object> params, String key, boolean isBlock) {
        String stripped = line.stripLeading();
        int indent = line.indexOf(stripped), subSep = stripped.indexOf(':');
        if ("steps".equals(key)) {
            handleNestedList(params, key, stripped);
            return;
        }
        if (!isBlock && subSep > 0 && !stripped.substring(0, subSep).contains(" ")) {
            String subKey = stripped.substring(0, subSep).trim(), subVal = stripQuotes(stripped.substring(subSep + 1).trim());
            Map<String, Object> parent = getOrCreateParent(params, key, indent);
            parent.put(subKey, subVal);
            lastKeys.put(indent, subKey);
            return;
        }
        if (existing instanceof String s) params.put(key, s + stripped + "\n");
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
    @SuppressWarnings("unchecked")
    private void handleNestedList(Map<String, Object> params, String key, String line) {
        List<Map<String, Object>> list;
        Object existing = params.get(key);
        if (existing instanceof List) list = (List<Map<String, Object>>) existing;
        else {
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
                lastMap.put(itemContent.substring(0, sep).trim(), stripQuotes(itemContent.substring(sep + 1).trim()));
            }
        }
    }
    private Map<String, Object> initListItem(List<Map<String, Object>> list, boolean isNewItem, String itemContent) {
        if (isNewItem && itemContent.startsWith("name:")) {
            Map<String, Object> newMap = new LinkedHashMap<>();
            list.add(newMap);
            return newMap;
        } else if (!list.isEmpty()) return list.get(list.size() - 1);
        return null;
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
    private String nvl(String v) { return v != null ? v : ""; }
}
