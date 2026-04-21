package com.prodbuddy.recipes;

import com.prodbuddy.core.tool.ToolError;
import com.prodbuddy.core.tool.ToolResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility to generate human-readable summaries of tool responses.
 * Used for virtual variable resolution and CLI reporting.
 */
public final class RecipeStepSummarizer {

    public static String summarize(ToolResponse response) {
        if (response.success()) {
            return summarizeSuccess(response.data());
        } else {
            return summarizeErrors(response.errors());
        }
    }

    public static String extractTrend(ToolResponse response) {
        if (!response.success()) {
            return "";
        }
        Map<String, Object> data = unwrap(response.data());
        Object bodyObj = data.get("body");
        if (!(bodyObj instanceof String body) || body.isBlank()) {
            return "";
        }
        return extractTimeseries(body);
    }

    private static String summarizeSuccess(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "Completed with no payload.";
        }
        Map<String, Object> effectiveData = unwrap(data);
        List<String> parts = new ArrayList<>();
        addStatus(effectiveData, parts);
        addResults(effectiveData, parts);
        addMatches(effectiveData, parts);
        addNewRelicEntities(effectiveData, parts);

        if (parts.isEmpty()) {
            java.util.Set<String> keys = new java.util.TreeSet<>(effectiveData.keySet());
            if (effectiveData.get("body") instanceof Map bod) {
                keys.addAll(((Map<String, Object>) bod).keySet());
                keys.remove("body");
            }
            return "fields=" + keys;
        }
        return String.join(", ", parts);
    }

    private static Map<String, Object> unwrap(Map<String, Object> data) {
        if (data.containsKey("result") && data.get("result") instanceof Map) {
            return (Map<String, Object>) data.get("result");
        }
        return data;
    }

    private static void addStatus(Map<String, Object> data, List<String> parts) {
        Object status = data.get("status");
        if (status != null) {
            parts.add("status=" + status);
        }
    }

    private static void addResults(Map<String, Object> data, List<String> parts) {
        Object results = data.get("results");
        if (results instanceof List<?> list) {
            parts.add("results=" + list.size());
        } else if (data.containsKey("body") && data.get("body") instanceof String body) {
            if (body.contains("\"count\":") || body.contains("count")) {
                parts.add("found errors in body");
            }
            String trend = extractTimeseries(body);
            if (!trend.isEmpty()) {
                parts.add("trend=" + trend);
            }
        }
    }

    private static String extractTimeseries(String body) {
        // Simple regex-based extraction of "count":X or similar from TIMESERIES JSON
        List<String> values = new ArrayList<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"count\":\\s*(\\d+)");
        java.util.regex.Matcher m = p.matcher(body);
        while (m.find()) {
            values.add(m.group(1));
        }
        return String.join(",", values);
    }

    private static void addMatches(Map<String, Object> data, List<String> parts) {
        Object matches = data.get("matches");
        if (matches instanceof List<?> list) {
            parts.add("matches=" + list.size());
        }
    }

    private static void addNewRelicEntities(Map<String, Object> data, List<String> parts) {
        Object bodyObj = data.get("body");
        if (!(bodyObj instanceof String body) || body.isBlank()) return;

        // Simple extraction for demo purposes since we don't have a JSON parser here
        // Looking for "guid":"...","name":"..."
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"guid\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher m = p.matcher(body);
        while (m.find()) {
            parts.add(m.group(2) + " (" + m.group(1) + ")");
        }
    }

    private static String summarizeErrors(List<ToolError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "No error details available.";
        }
        ToolError first = errors.get(0);
        return first.code() + ": " + first.message();
    }
}
