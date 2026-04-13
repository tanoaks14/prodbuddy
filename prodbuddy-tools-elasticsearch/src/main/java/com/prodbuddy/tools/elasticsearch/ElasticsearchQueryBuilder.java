package com.prodbuddy.tools.elasticsearch;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class ElasticsearchQueryBuilder {

    private final ObjectMapper mapper;

    public ElasticsearchQueryBuilder() {
        this.mapper = new ObjectMapper();
    }

    public String buildMatchAll(int size) {
        return "{\"query\":{\"match_all\":{}},\"size\":" + size + "}";
    }

    public String buildFieldMatch(String field, String value, int size) {
        return "{\"query\":{\"match\":{\"" + field + "\":\"" + value + "\"}},\"size\":" + size + "}";
    }

    public String fromPayload(Map<String, Object> payload) {
        String rawBody = String.valueOf(payload.getOrDefault("body", "")).trim();
        if (!rawBody.isBlank()) {
            return rawBody;
        }
        Object queryDsl = payload.get("queryDsl");
        if (queryDsl instanceof Map<?, ?> map) {
            return asJson(map);
        }
        String queryString = String.valueOf(payload.getOrDefault("queryString", "")).trim();
        int size = Integer.parseInt(String.valueOf(payload.getOrDefault("size", "10")));
        if (!queryString.isBlank()) {
            return buildQueryString(queryString, size);
        }
        String field = String.valueOf(payload.getOrDefault("field", ""));
        String value = String.valueOf(payload.getOrDefault("value", ""));
        if (field.isBlank() || value.isBlank()) {
            return buildMatchAll(size);
        }
        return buildFieldMatch(field, value, size);
    }

    private String buildQueryString(String queryString, int size) {
        return "{\"query\":{\"query_string\":{\"query\":\"" + queryString + "\"}},\"size\":" + size + "}";
    }

    private String asJson(Map<?, ?> body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid queryDsl payload", exception);
        }
    }
}
