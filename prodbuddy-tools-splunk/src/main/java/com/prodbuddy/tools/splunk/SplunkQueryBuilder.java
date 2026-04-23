package com.prodbuddy.tools.splunk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;

/** Query builder for Splunk. */
public final class SplunkQueryBuilder {

    /** Default fallback search. */
    private static final String DEFAULT_SEARCH =
            "search index=_internal | head 10";

    /**
     * Resolves the search query.
     * @param request Tool request.
     * @param context Tool context.
     * @return Normalized search query.
     */
    public String resolveSearch(final ToolRequest request,
                                final ToolContext context) {
        String raw = extractSearchFromPayload(request.payload());
        if (raw != null) {
            return normalizeSearch(raw);
        }

        String composed = composeSearch(request.payload());
        if (!composed.isBlank()) {
            return normalizeSearch(composed);
        }

        return normalizeSearch(
                String.valueOf(
                        request.payload().getOrDefault(
                                "search",
                                context.envOrDefault("SPLUNK_DEFAULT_SEARCH",
                                        DEFAULT_SEARCH)
                        )
                )
        );
    }

    private String extractSearchFromPayload(final Map<String, Object> payload) {
        String[] keys = {"search", "query", "queryString"};
        for (String key : keys) {
            Object val = payload.get(key);
            if (val != null && !String.valueOf(val).isBlank()) {
                return String.valueOf(val);
            }
        }
        return null;
    }

    /**
     * Resolves the API path.
     * @param operation Operation name.
     * @param payload Request payload.
     * @return API path.
     */
    public String resolvePath(final String operation,
                              final Map<String, Object> payload) {
        Object customPath = payload.get("path");
        if (customPath != null && !String.valueOf(customPath).isBlank()) {
            return String.valueOf(customPath);
        }

        if ("oneshot".equals(operation)) {
            return "/services/search/jobs/oneshot";
        }
        if ("results".equals(operation)) {
            String sid = String.valueOf(payload.getOrDefault("sid", ""))
                    .trim();
            if (!sid.isBlank()) {
                return "/services/search/jobs/" + sid + "/results";
            }
        }
        return "/services/search/jobs";
    }

    /**
     * Builds the request body.
     * @param operation Operation name.
     * @param payload Request payload.
     * @param search Normalized search query.
     * @return URL-encoded body.
     */
    public String buildBody(final String operation,
                            final Map<String, Object> payload,
                            final String search) {
        Object rawBody = payload.get("rawBody");
        if (rawBody != null && !String.valueOf(rawBody).isBlank()) {
            return String.valueOf(rawBody);
        }

        StringBuilder builder = new StringBuilder();
        if (!"results".equals(operation)) {
            String searchKey = String.valueOf(payload.getOrDefault(
                    "searchKey", "search"));
            builder.append(searchKey).append("=").append(
                    URLEncoder.encode(search, StandardCharsets.UTF_8));
        }

        appendStandardParams(builder, payload);

        Object extra = payload.get("params");
        if (extra instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                appendOptional(builder, String.valueOf(entry.getKey()),
                        entry.getValue());
            }
        }

        return builder.toString();
    }

    private void appendStandardParams(final StringBuilder builder,
                                      final Map<String, Object> payload) {
        appendOptional(builder, "earliest_time", payload.get("earliestTime"));
        appendOptional(builder, "latest_time", payload.get("latestTime"));
        appendOptional(builder, "count", payload.get("count"));
        appendOptional(builder, "exec_mode", payload.get("execMode"));
        
        boolean hasOutputMode = payload.containsKey("outputMode") || payload.containsKey("output_mode");
        if (!hasOutputMode && payload.get("params") instanceof Map<?, ?> pMap) {
            hasOutputMode = pMap.containsKey("outputMode") || pMap.containsKey("output_mode");
        }
        
        if (!hasOutputMode) {
            appendOptional(builder, "output_mode", "json");
        }
    }

    private String composeSearch(final Map<String, Object> payload) {
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

    private void appendClause(final List<String> clauses,
                              final String key, final Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        clauses.add(key + "=" + String.valueOf(value));
    }

    private String normalizeSearch(final String search) {
        String trimmed = search == null ? "" : search.trim();
        if (trimmed.isBlank()) {
            return DEFAULT_SEARCH;
        }
        if (trimmed.startsWith("search ") || trimmed.startsWith("|")) {
            return trimmed;
        }
        return "search " + trimmed;
    }

    private void appendOptional(final StringBuilder builder,
                                final String key, final Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("&");
        }
        builder.append(key).append("=").append(URLEncoder.encode(
                String.valueOf(value), StandardCharsets.UTF_8));
    }
}
