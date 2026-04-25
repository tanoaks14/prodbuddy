package com.prodbuddy.core.system;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for loading and rendering externalized queries.
 */
public final class QueryService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}|\\{\\{([^}]+)\\}\\}");

    /**
     * Renders a query from the classpath with given parameters.
     * @param path The path to the query resource (relative to /queries/)
     * @param params The parameters for interpolation
     * @return The rendered query string
     * @throws RuntimeException if the resource is not found or cannot be read
     */
    public String render(String path, Map<String, Object> params) {
        String template = cache.computeIfAbsent(path, this::loadTemplate);
        if (params == null || params.isEmpty()) {
            return template;
        }
        return interpolate(template, params);
    }

    /**
     * Check if a query resource exists.
     * @param path The path to the query resource
     * @return true if exists
     */
    public boolean exists(String path) {
        if (cache.containsKey(path)) return true;
        String fullPath = "/queries/" + path;
        try (InputStream is = getClass().getResourceAsStream(fullPath)) {
            return is != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String loadTemplate(String path) {
        String fullPath = "/queries/" + (path.startsWith("/") ? path.substring(1) : path);
        try (InputStream is = getClass().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new RuntimeException("Query resource not found: " + fullPath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load query: " + path, e);
        }
    }

    private String interpolate(String template, Map<String, Object> params) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(template, lastEnd, matcher.start());
            String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            key = key.trim();
            Object value = params.get(key);
            result.append(value != null ? String.valueOf(value) : matcher.group(0));
            lastEnd = matcher.end();
        }
        result.append(template.substring(lastEnd));
        return result.toString();
    }
}
