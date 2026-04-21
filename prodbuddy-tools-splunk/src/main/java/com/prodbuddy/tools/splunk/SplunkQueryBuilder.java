package com.prodbuddy.tools.splunk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;

public final class SplunkQueryBuilder {

    public String resolveSearch(ToolRequest request, ToolContext context) {
        Object raw = request.payload().get("search");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            return normalizeSearch(String.valueOf(raw));
        }

        Object query = request.payload().get("query");
        if (query != null && !String.valueOf(query).isBlank()) {
            return normalizeSearch(String.valueOf(query));
        }

        Object queryString = request.payload().get("queryString");
        if (queryString != null && !String.valueOf(queryString).isBlank()) {
            return normalizeSearch(String.valueOf(queryString));
        }

        String composed = composeSearch(request.payload());
        if (!composed.isBlank()) {
            return normalizeSearch(composed);
        }

        return normalizeSearch(
                String.valueOf(
                        request.payload().getOrDefault(
                                "search",
                                context.envOrDefault("SPLUNK_DEFAULT_SEARCH", "search index=_internal | head 10")
                        )
                )
        );
    }

    public String resolvePath(String operation, Map<String, Object> payload) {
        if ("oneshot".equals(operation)) {
            return "/services/search/jobs/oneshot";
        }
        if ("results".equals(operation)) {
            String sid = String.valueOf(payload.getOrDefault("sid", "")).trim();
            if (!sid.isBlank()) {
                return "/services/search/jobs/" + sid + "/results";
            }
        }
        return "/services/search/jobs";
    }

    public String buildBody(String operation, Map<String, Object> payload, String search) {
        StringBuilder builder = new StringBuilder();
        if (!"results".equals(operation)) {
            builder.append("search=").append(URLEncoder.encode(search, StandardCharsets.UTF_8));
        }

        appendOptional(builder, "earliest_time", payload.get("earliestTime"));
        appendOptional(builder, "latest_time", payload.get("latestTime"));
        appendOptional(builder, "count", payload.get("count"));
        appendOptional(builder, "exec_mode", payload.get("execMode"));
        appendOptional(builder, "output_mode", payload.getOrDefault("outputMode", "json"));
        return builder.toString();
    }

    private String composeSearch(Map<String, Object> payload) {
        List<String> clauses = new ArrayList<>();
        appendClause(clauses, "index", payload.get("index"));
        appendClause(clauses, "host", payload.get("host"));
        appendClause(clauses, "source", payload.get("source"));
        appendClause(clauses, "sourcetype", payload.get("sourcetype"));

        Object terms = payload.get("terms");
        if (terms != null && !String.valueOf(terms).isBlank()) {
            clauses.add(String.valueOf(terms));
        }
        return String.join(" ", clauses).trim();
    }

    private void appendClause(List<String> clauses, String key, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        clauses.add(key + "=" + String.valueOf(value));
    }

    private String normalizeSearch(String search) {
        String trimmed = search == null ? "" : search.trim();
        if (trimmed.isBlank()) {
            return "search index=_internal | head 10";
        }
        return (trimmed.startsWith("search ") || trimmed.startsWith("|")) ? trimmed : "search " + trimmed;
    }

    private void appendOptional(StringBuilder builder, String key, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("&");
        }
        builder.append(key)
                .append("=")
                .append(URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
    }
}
