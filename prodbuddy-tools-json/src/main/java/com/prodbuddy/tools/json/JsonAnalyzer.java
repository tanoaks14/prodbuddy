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
        TraceResult tr = walkWithTrace(jsonStr, path);
        JsonNode node = tr.node();
        if (node == null || node.isMissingNode()) {
            return false;
        }
        return expected.equals(node.asText());
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
        if (paths != null) {
            for (Map.Entry<String, String> entry : paths.entrySet()) {
                TraceResult tr = walkWithTrace(jsonStr, entry.getValue());
                if (tr.node() != null && !tr.node().isMissingNode()) {
                    results.put(entry.getKey(), tr.node().isContainerNode() ? tr.node().toString() : tr.node().asText());
                }
            }
        }
        if (regexList != null) {
            extractRegex(jsonStr, regexList, results);
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
            if (path == null || path.isBlank() || "$".equals(path.trim())) {
                return new TraceResult(root, trace);
            }
            // Replace non-breaking spaces and trim
            String normalizedPath = path.replace("\u00A0", " ").trim();
            return walkPathParts(root, normalizedPath.split("\\."), trace);
        } catch (JsonProcessingException e) {
            trace.add("ERROR: " + e.getMessage());
            return new TraceResult(null, trace);
        }
    }

    private TraceResult walkPathParts(final JsonNode root, final String[] parts,
                                      final List<String> trace) {
        JsonNode current = root;
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) continue;
            current = walkStep(current, trimmedPart, trace);
            if (current == null || current.isMissingNode()) {
                return new TraceResult(null, trace);
            }
        }
        return new TraceResult(current, trace);
    }

    private JsonNode walkStep(final JsonNode node, final String part,
                              final List<String> trace) {
        if (node == null || node.isMissingNode()) {
            trace.add(part + " -> MISSING (parent is null/missing)");
            return null;
        }
        JsonNode next = part.contains("[") ? extractArrayIndex(node, part) : node.path(part);
        if (next == null || next.isMissingNode()) {
            StringBuilder msg = new StringBuilder(part).append(" -> MISSING");
            if (node.isObject()) {
                List<String> keys = new ArrayList<>();
                node.fieldNames().forEachRemaining(keys::add);
                msg.append(" (Available keys: ").append(keys).append(")");
            } else if (node.isArray()) {
                msg.append(" (Node is an Array of size ").append(node.size()).append(")");
            } else {
                msg.append(" (Node is a Value: ").append(node.getNodeType()).append(")");
            }
            trace.add(msg.toString());
            return null;
        }
        trace.add(part + " -> FOUND (" + next.getNodeType() + ")");
        return next;
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
