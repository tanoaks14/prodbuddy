package com.prodbuddy.tools.kubectl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KubectlCommandBuilder {

    public List<String> build(String operation, Map<String, Object> payload, String namespace) {
        if ("raw".equalsIgnoreCase(operation) || "command".equalsIgnoreCase(operation)) {
            return buildRaw(payload);
        }

        List<String> command = new ArrayList<>();
        command.add(String.valueOf(payload.getOrDefault("binary", "kubectl")));
        command.add(operation.toLowerCase());
        addNamespace(command, namespace);
        addResource(command, payload);
        addOptional(command, payload, "name");
        addOptional(command, payload, "file");
        addArgs(command, payload);
        addFlags(command, payload);
        return command;
    }

    private List<String> buildRaw(Map<String, Object> payload) {
        String raw = String.valueOf(payload.getOrDefault("command", "kubectl get pods"));
        return tokenize(raw);
    }

    private void addNamespace(List<String> command, String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return;
        }
        command.add("--namespace");
        command.add(namespace);
    }

    private void addResource(List<String> command, Map<String, Object> payload) {
        String resource = String.valueOf(payload.getOrDefault("resource", ""));
        if (!resource.isBlank()) {
            command.add(resource);
        }
    }

    private void addOptional(List<String> command, Map<String, Object> payload, String key) {
        String value = String.valueOf(payload.getOrDefault(key, ""));
        if (!value.isBlank()) {
            command.add(value);
        }
    }

    private void addArgs(List<String> command, Map<String, Object> payload) {
        Object args = payload.get("args");
        if (!(args instanceof List<?> list)) {
            return;
        }
        for (Object value : list) {
            command.add(String.valueOf(value));
        }
    }

    @SuppressWarnings("unchecked")
    private void addFlags(List<String> command, Map<String, Object> payload) {
        Object flags = payload.get("flags");
        if (!(flags instanceof Map<?, ?> map)) {
            return;
        }
        Map<String, Object> normalized = new LinkedHashMap<>((Map<String, Object>) map);
        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean boolValue) {
                if (boolValue) {
                    command.add("--" + key);
                }
                continue;
            }
            String text = String.valueOf(value);
            if (!text.isBlank()) {
                command.add("--" + key);
                command.add(text);
            }
        }
    }

    private List<String> tokenize(String raw) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char ch : raw.toCharArray()) {
            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (Character.isWhitespace(ch) && !inQuotes) {
                addToken(tokens, current);
                continue;
            }
            current.append(ch);
        }
        addToken(tokens, current);
        return tokens;
    }

    private void addToken(List<String> tokens, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        tokens.add(current.toString());
        current.setLength(0);
    }
}
