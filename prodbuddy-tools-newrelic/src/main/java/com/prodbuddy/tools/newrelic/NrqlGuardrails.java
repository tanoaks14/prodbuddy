package com.prodbuddy.tools.newrelic;

import java.util.Set;

public final class NrqlGuardrails {

    private static final Set<String> ALLOWED_METRICS = Set.of(
            "errors", "latency", "throughput", "apdex", "error_rate"
    );
    private static final Set<String> ALLOWED_GROUP_BY = Set.of(
            "", "appName", "entity.guid", "host", "service"
    );

    private final int maxTimeWindowMinutes;
    private final int maxLimit;
    private final int maxFilters;

    public NrqlGuardrails(int maxTimeWindowMinutes, int maxLimit, int maxFilters) {
        this.maxTimeWindowMinutes = maxTimeWindowMinutes;
        this.maxLimit = maxLimit;
        this.maxFilters = maxFilters;
    }

    public static NrqlGuardrails defaults() {
        return new NrqlGuardrails(30 * 24 * 60, 10_000, 5);
    }

    public boolean isMetricAllowed(String metric) {
        return ALLOWED_METRICS.contains(metric);
    }

    public boolean isGroupByAllowed(String groupBy) {
        return ALLOWED_GROUP_BY.contains(groupBy);
    }

    public int maxTimeWindowMinutes() {
        return maxTimeWindowMinutes;
    }

    public int maxLimit() {
        return maxLimit;
    }

    public int maxFilters() {
        return maxFilters;
    }
}
