package com.prodbuddy.tools.newrelic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builder for NRQL query strings.
 */
public final class NrqlQueryBuilder {

    /**
     * Builds a NRQL query from a request.
     * @param request the query request
     * @return the NRQL string
     */
    public String build(final NrqlQueryRequest request) {
        StringBuilder nrql = new StringBuilder();
        nrql.append(selectForMetric(request.metric()));
        nrql.append(" FROM ").append(eventForMetric(request.metric()));
        appendFilters(nrql, request.filters());
        nrql.append(" TIMESERIES");
        nrql.append(" SINCE ").append(request.timeWindowMinutes())
            .append(" minutes ago");
        appendGroupBy(nrql, request.groupBy());
        nrql.append(" LIMIT ").append(request.limit());
        return nrql.toString();
    }

    private String selectForMetric(final String metric) {
        return switch (metric) {
            case "errors" ->
                "SELECT count(*)";
            case "latency", "latency.transaction.rate" ->
                "SELECT average(duration)";
            case "apdex" ->
                "SELECT average(apdex)";
            case "error_rate" ->
                "SELECT percentage(count(*), WHERE error IS true)";
            case "throughput" ->
                "SELECT rate(count(*), 1 minute)";
            default ->
                "SELECT rate(count(*), 1 minute)";
        };
    }

    private String eventForMetric(final String metric) {
        return switch (metric) {
            case "errors" ->
                "TransactionError";
            default ->
                "Transaction";
        };
    }

    private void appendFilters(final StringBuilder nrql, final Map<String, String> filters) {
        if (filters.isEmpty()) {
            return;
        }
        List<String> expressions = new ArrayList<>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            expressions.add(entry.getKey() + " = '"
                + sanitize(entry.getValue()) + "'");
        }
        nrql.append(" WHERE ").append(String.join(" AND ", expressions));
    }

    private void appendGroupBy(final StringBuilder nrql, final String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return;
        }
        nrql.append(" FACET ").append(groupBy);
    }

    private String sanitize(final String value) {
        return value.replace("'", "");
    }
}
