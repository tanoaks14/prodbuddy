package com.prodbuddy.tools.newrelic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class NrqlQueryBuilder {

    public String build(NrqlQueryRequest request) {
        StringBuilder nrql = new StringBuilder();
        nrql.append(selectForMetric(request.metric()));
        nrql.append(" FROM ").append(eventForMetric(request.metric()));
        appendFilters(nrql, request.filters());
        nrql.append(" TIMESERIES");
        nrql.append(" SINCE ").append(request.timeWindowMinutes()).append(" minutes ago");
        appendGroupBy(nrql, request.groupBy());
        nrql.append(" LIMIT ").append(request.limit());
        return nrql.toString();
    }

    private String selectForMetric(String metric) {
        return switch (metric) {
            case "errors" ->
                "SELECT count(*)";
            case "latency" ->
                "SELECT average(duration)";
            case "apdex" ->
                "SELECT average(apdex)";
            case "error_rate" ->
                "SELECT percentage(count(*), WHERE error IS true)";
            default ->
                "SELECT rate(count(*), 1 minute)";
        };
    }

    private String eventForMetric(String metric) {
        return switch (metric) {
            case "errors" ->
                "TransactionError";
            default ->
                "Transaction";
        };
    }

    private void appendFilters(StringBuilder nrql, Map<String, String> filters) {
        if (filters.isEmpty()) {
            return;
        }
        List<String> expressions = new ArrayList<>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            expressions.add(entry.getKey() + " = '" + sanitize(entry.getValue()) + "'");
        }
        nrql.append(" WHERE ").append(String.join(" AND ", expressions));
    }

    private void appendGroupBy(StringBuilder nrql, String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return;
        }
        nrql.append(" FACET ").append(groupBy);
    }

    private String sanitize(String value) {
        return value.replace("'", "");
    }
}
