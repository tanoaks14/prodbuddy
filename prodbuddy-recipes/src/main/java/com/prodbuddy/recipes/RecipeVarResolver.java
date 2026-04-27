package com.prodbuddy.recipes;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves ${PLACEHOLDER} syntax in recipe parameter values.
 *
 * <p>Two resolution strategies applied in order:
 * <ul>
 *   <li>${ENV_VAR} — looks up the key in ToolContext environment</li>
 *   <li>${stepName.field[0].sub} — dot-path walk into a prior step's ToolResponse data</li>
 * </ul>
 * Unresolved placeholders are left as-is (no hard failure).
 */
public final class RecipeVarResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    public Object resolve(String value, ToolContext ctx, Map<String, ToolResponse> stepResults) {
        return resolve(value, ctx, stepResults, Map.of());
    }

    public Object resolve(String value, ToolContext ctx, Map<String, ToolResponse> stepResults, Map<String, Object> localVars) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        
        // Optimization: If the value is EXACTLY one placeholder, return the raw object.
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (matcher.matches()) {
            return resolveKey(matcher.group(1), ctx, stepResults, localVars);
        }

        matcher.reset();
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object resolved = resolveKey(key, ctx, stepResults, localVars);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(resolved)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public Map<String, Object> resolveAll(
            Map<String, Object> rawParams,
            ToolContext ctx,
            Map<String, ToolResponse> stepResults
    ) {
        return resolveAll(rawParams, ctx, stepResults, Map.of());
    }

    public Map<String, Object> resolveAll(
            Map<String, Object> rawParams,
            ToolContext ctx,
            Map<String, ToolResponse> stepResults,
            Map<String, Object> localVars
    ) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
            resolved.put(entry.getKey(), resolveRecursively(entry.getValue(), ctx, stepResults, localVars));
        }
        return resolved;
    }

    private Object resolveRecursively(Object val, ToolContext ctx, Map<String, ToolResponse> stepResults, Map<String, Object> localVars) {
        if (val instanceof String s) {
            return resolve(s, ctx, stepResults, localVars);
        }
        if (val instanceof Map<?, ?> map) {
            Map<String, Object> resolvedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                resolvedMap.put(String.valueOf(entry.getKey()), resolveRecursively(entry.getValue(), ctx, stepResults, localVars));
            }
            return resolvedMap;
        }
        if (val instanceof List<?> list) {
            List<Object> resolvedList = new java.util.ArrayList<>();
            for (Object item : list) {
                resolvedList.add(resolveRecursively(item, ctx, stepResults, localVars));
            }
            return resolvedList;
        }
        return val;
    }

    private Object resolveKey(String key, ToolContext ctx, Map<String, ToolResponse> stepResults, Map<String, Object> localVars) {
        // 1. Check local scope first (loop variables)
        if (localVars != null && localVars.containsKey(key)) {
            return localVars.get(key);
        }

        int dot = key.indexOf('.');
        if (dot < 0) {
            String val = ctx.envOrDefault(key, null);
            if (val != null) {
                return val;
            }
            // Smart Defaults for critical variables to prevent tool crashes
            return switch (key) {
                case "PRODBUDDY_PROJECT_PATH" -> ".";
                case "DIAGNOSTIC_WINDOW" -> "60";
                case "NEWRELIC_ACCOUNT_ID" -> "0";
                default -> "${" + key + "}";
            };
        }
        String stepName = key.substring(0, dot);
        String path = key.substring(dot + 1);
        return resolveStepPath(stepName, path, stepResults);
    }

    private Object resolveStepPath(final String stepName,
                                   final String path,
                                   final Map<String, ToolResponse> stepResults) {
        String stepKey = stepName;
        if (!stepResults.containsKey(stepKey)) {
            // Support shortened step names (e.g. "Step 2" matches "Step 2: Extract")
            stepKey = stepResults.keySet().stream()
                    .filter(k -> k.startsWith(stepName))
                    .findFirst()
                    .orElse(stepName);
        }

        ToolResponse response = stepResults.get(stepKey);
        if (response == null) {
            return "${" + stepName + "." + path + "}";
        }

        String special = handleSpecialFields(response, path);
        if (special != null) {
            return special;
        }

        Map<String, Object> data = unwrapIfOrchestrator(response.data(), path);
        Object value = walkPath(data, path);
        return value != null ? value : "${" + stepName + "." + path + "}";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapIfOrchestrator(Map<String, Object> data, String path) {
        if (!data.containsKey("result") || "result".equals(path) || "tool".equals(path) || "iteration".equals(path)) {
            return data;
        }
        Object nested = data.get("result");
        if (!(nested instanceof Map m)) {
            return data;
        }
        // Smart Body Unwrapping: if the key isn't in outer map but is in body, dive in.
        if (m.containsKey("body") && !m.containsKey(path) && m.get("body") instanceof Map) {
            return (Map<String, Object>) m.get("body");
        }
        return (Map<String, Object>) m;
    }

    private String handleSpecialFields(ToolResponse response, String path) {
        if ("summary".equals(path)) {
            return RecipeStepSummarizer.summarize(response);
        }
        if ("success".equals(path)) {
            return String.valueOf(response.success());
        }
        if ("trend".equals(path)) {
            return RecipeStepSummarizer.extractTrend(response);
        }
        return null;
    }

    private Object walkPath(Object node, String path) {
        if (node == null || path.isBlank()) {
            return node;
        }

        // SMART DIVING: If the current node is a JSON string, parse it before walking deeper.
        if (node instanceof String s && (s.trim().startsWith("{") || s.trim().startsWith("["))) {
            Object parsed = tryParseJson(s);
            if (parsed != null) {
                node = parsed;
            }
        }

        String[] segments = path.split("\\.", 2);
        String head = segments[0];
        String rest = segments.length > 1 ? segments[1] : "";
        if (head.endsWith("]")) {
            return navigateIndex(node, head, rest);
        }
        return navigateKey(node, head, rest);
    }

    @SuppressWarnings("unchecked")
    private Object navigateKey(Object node, String key, String rest) {
        if (!(node instanceof Map)) {
            return null;
        }
        Object child = ((Map<String, Object>) node).get(key);
        return rest.isBlank() ? child : walkPath(child, rest);
    }

    private Object tryParseJson(String s) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (s.trim().startsWith("{")) {
                return mapper.readValue(s, Map.class);
            } else {
                return mapper.readValue(s, List.class);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Object navigateIndex(Object node, String segment, String rest) {
        int bracketOpen = segment.indexOf('[');
        String key = segment.substring(0, bracketOpen);
        int index = parseIndex(segment, bracketOpen);
        Object target = key.isBlank() ? node : navigateKey(node, key, "");
        return navigateList(target, index, rest);
    }

    private int parseIndex(String segment, int bracketOpen) {
        String inside = segment.substring(bracketOpen + 1, segment.length() - 1);
        try {
            return Integer.parseInt(inside);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private Object navigateList(Object node, int index, String rest) {
        if (!(node instanceof List) || index < 0) {
            return null;
        }
        List<Object> list = (List<Object>) node;
        if (index >= list.size()) {
            return null;
        }
        Object item = list.get(index);
        return rest.isBlank() ? item : walkPath(item, rest);
    }
}
