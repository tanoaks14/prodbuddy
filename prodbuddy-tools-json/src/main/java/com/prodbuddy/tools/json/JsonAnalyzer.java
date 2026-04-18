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

    private JsonNode walk(JsonNode root, String path) {
        if (path == null || path.isBlank() || "$".equals(path)) {
            return root;
        }
        String[] parts = path.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null) { return null; }
            if (part.contains("[")) {
                current = extractArrayIndex(current, part);
            } else {
                current = current.path(part);
            }
        }
        return current;
    }

    private JsonNode extractArrayIndex(JsonNode current, String part) {
        int splitIdx = part.indexOf('[');
        String key = part.substring(0, splitIdx);
        int arrIdx = Integer.parseInt(part.substring(splitIdx + 1, part.length() - 1));
        if (!key.isEmpty()) {
            current = current.path(key);
        }
        return current.path(arrIdx);
    }
}
