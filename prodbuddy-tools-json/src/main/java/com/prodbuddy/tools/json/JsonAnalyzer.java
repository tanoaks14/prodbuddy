package com.prodbuddy.tools.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class JsonAnalyzer {
    private final ObjectMapper mapper;

    public JsonAnalyzer() {
        this.mapper = new ObjectMapper();
    }

    public boolean assertPathEq(String jsonStr, String path, String expected) {
        try {
            JsonNode root = mapper.readTree(jsonStr);
            JsonNode node = walk(root, path);
            if (node == null || node.isMissingNode()) {
                return false;
            }
            return expected.equals(node.asText());
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    public List<String> searchKey(String jsonStr, String key) {
        List<String> paths = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(jsonStr);
            traverse(root, "$", key, paths);
            return paths;
        } catch (JsonProcessingException e) {
            return paths;
        }
    }

    public Map<String, Object> extract(String jsonStr, Map<String, String> paths, Map<String, String> regexList) {
        Map<String, Object> results = new java.util.HashMap<>();
        try {
            JsonNode root = mapper.readTree(jsonStr);
            if (paths != null) {
                for (Map.Entry<String, String> entry : paths.entrySet()) {
                    JsonNode node = walk(root, entry.getValue());
                    if (node != null && !node.isMissingNode()) {
                        results.put(entry.getKey(), node.isContainerNode() ? node.toString() : node.asText());
                    }
                }
            }
            if (regexList != null) {
                extractRegex(jsonStr, regexList, results);
            }
        } catch (JsonProcessingException e) {
            // partly filled
        }
        return results;
    }

    private void extractRegex(String content, Map<String, String> regexList, Map<String, Object> results) {
        for (Map.Entry<String, String> entry : regexList.entrySet()) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(entry.getValue());
            java.util.regex.Matcher m = p.matcher(content);
            List<String> matches = new ArrayList<>();
            while (m.find()) {
                matches.add(m.groupCount() > 0 ? m.group(1) : m.group());
            }
            if (!matches.isEmpty()) {
                results.put(entry.getKey(), String.join(",", matches));
            }
        }
    }

    private void traverse(JsonNode node, String currentPath, String targetKey, List<String> result) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String nextPath = currentPath + "." + field.getKey();
                if (targetKey.equals(field.getKey())) {
                    result.add(nextPath);
                }
                traverse(field.getValue(), nextPath, targetKey, result);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                traverse(node.get(i), currentPath + "[" + i + "]", targetKey, result);
            }
        }
    }

    /**
     * Walk the JSON tree and return result with trace.
     * @param jsonStr raw JSON
     * @param path dot path
     * @return result with trace
     */
    public TraceResult walkWithTrace(final String jsonStr, final String path) {
        List<String> trace = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(jsonStr);
            trace.add("root");
            if (path == null || path.isBlank() || "$".equals(path)) {
                return new TraceResult(root, trace);
            }
            String[] parts = path.split("\\.");
            JsonNode current = root;
            for (String part : parts) {
                if (current == null || current.isMissingNode()) {
                    trace.add(part + " -> MISSING (previous node was null)");
                    return new TraceResult(null, trace);
                }
                if (part.contains("[")) {
                    current = extractArrayIndex(current, part);
                } else {
                    current = current.path(part);
                }
                if (current == null || current.isMissingNode()) {
                    trace.add(part + " -> MISSING");
                    return new TraceResult(null, trace);
                }
                trace.add(part + " -> FOUND (" + current.getNodeType() + ")");
            }
            return new TraceResult(current, trace);
        } catch (JsonProcessingException e) {
            trace.add("ERROR: " + e.getMessage());
            return new TraceResult(null, trace);
        }
    }

    public static record TraceResult(JsonNode node, List<String> trace) { }

    private JsonNode extractArrayIndex(final JsonNode current, final String part) {
        int splitIdx = part.indexOf('[');
        String key = part.substring(0, splitIdx);
        int arrIdx = Integer.parseInt(part.substring(splitIdx + 1, part.length() - 1));
        JsonNode node = current;
        if (!key.isEmpty()) {
            node = node.path(key);
        }
        return node.path(arrIdx);
    }
}
