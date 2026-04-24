package com.prodbuddy.tools.newrelic;

import java.util.Collections;
import java.util.Map;

/**
 * Data record for NRQL query parameters.
 * @param metric the metric name
 * @param filters map of filter keys and values
 * @param timeWindowMinutes the time window in minutes
 * @param limit the results limit
 * @param groupBy the field to facet by
 */
public record NrqlQueryRequest(
        String metric,
        Map<String, String> filters,
        int timeWindowMinutes,
        int limit,
        String groupBy
        ) {

    private static final int DEFAULT_WINDOW = 5;
    private static final int DEFAULT_LIMIT = 100;

    /** Canonical constructor. */
    public NrqlQueryRequest {
        filters = filters == null ? Map.of()
            : Collections.unmodifiableMap(filters);
        metric = metric == null || metric.isBlank() ? "throughput" : metric;
        timeWindowMinutes = timeWindowMinutes <= 0 ? DEFAULT_WINDOW
            : timeWindowMinutes;
        limit = limit <= 0 ? DEFAULT_LIMIT : limit;
        groupBy = groupBy == null ? "" : groupBy;
    }
}
