package com.prodbuddy.recipes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolResponse;

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

    public String resolve(String value, ToolContext ctx, Map<String, ToolResponse> stepResults) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String resolved = resolveKey(key, ctx, stepResults);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public Map<String, String> resolveAll(
            Map<String, String> rawParams,
            ToolContext ctx,
            Map<String, ToolResponse> stepResults
    ) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawParams.entrySet()) {
            resolved.put(entry.getKey(), resolve(entry.getValue(), ctx, stepResults));
        }
        return resolved;
    }

    private String resolveKey(String key, ToolContext ctx, Map<String, ToolResponse> stepResults) {
        int dot = key.indexOf('.');
        if (dot < 0) {
            return ctx.envOrDefault(key, "${" + key + "}");
        }
        String stepName = key.substring(0, dot);
        String path = key.substring(dot + 1);
        return resolveStepPath(stepName, path, stepResults);
    }

    private String resolveStepPath(String stepName, String path, Map<String, ToolResponse> stepResults) {
        ToolResponse response = stepResults.get(stepName);
        if (response == null || !response.success()) {
            return "${" + stepName + "." + path + "}";
        }
        Object value = walkPath(response.data(), path);
        return value != null ? String.valueOf(value) : "${" + stepName + "." + path + "}";
    }

    private Object walkPath(Object node, String path) {
        if (node == null || path.isBlank()) {
            return node;
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
