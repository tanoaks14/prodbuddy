package com.prodbuddy.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EnvFileLoader {

    public Map<String, String> load(Path filePath) {
        if (!Files.exists(filePath)) {
            return Map.of();
        }

        try {
            List<String> lines = Files.readAllLines(filePath);
            return parse(lines);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load env file: " + filePath, exception);
        }
    }

    private Map<String, String> parse(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            values.put(key, stripQuotes(value));
        }
        return values;
    }

    private String stripQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }
        boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
        boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
        return doubleQuoted || singleQuoted ? value.substring(1, value.length() - 1) : value;
    }
}
