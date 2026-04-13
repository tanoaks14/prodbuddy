package com.prodbuddy.tools.newrelic;

import java.util.Collections;
import java.util.Map;

public record NrqlQueryRequest(
        String metric,
        Map<String, String> filters,
        int timeWindowMinutes,
        int limit,
        String groupBy
        ) {

    public NrqlQueryRequest     {
        filters = filters == null ? Map.of() : Collections.unmodifiableMap(filters);
        metric = metric == null || metric.isBlank() ? "throughput" : metric;
        timeWindowMinutes = timeWindowMinutes <= 0 ? 5 : timeWindowMinutes;
        limit = limit <= 0 ? 100 : limit;
        groupBy = groupBy == null ? "" : groupBy;
    }
}
