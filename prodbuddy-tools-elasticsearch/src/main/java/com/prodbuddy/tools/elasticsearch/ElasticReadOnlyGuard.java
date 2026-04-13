package com.prodbuddy.tools.elasticsearch;

import java.util.Set;

public final class ElasticReadOnlyGuard {

    private static final Set<String> ALLOWED_ENDPOINTS = Set.of(
            "_search",
            "_count",
            "_msearch",
            "_field_caps",
            "_mapping",
            "_settings"
    );

    public boolean isAllowed(String endpoint, String method) {
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        String normalizedMethod = method == null ? "" : method.toUpperCase();
        if (normalizedEndpoint.startsWith("_cat/")) {
            return "GET".equals(normalizedMethod);
        }
        if (!ALLOWED_ENDPOINTS.contains(normalizedEndpoint)) {
            return false;
        }
        if ("_mapping".equals(normalizedEndpoint) || "_settings".equals(normalizedEndpoint)) {
            return "GET".equals(normalizedMethod);
        }
        return "GET".equals(normalizedMethod) || "POST".equals(normalizedMethod);
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "_search";
        }
        return endpoint.startsWith("/") ? endpoint.substring(1) : endpoint;
    }
}
