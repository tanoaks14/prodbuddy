package com.prodbuddy.tools.newrelic;

import java.util.Set;

/**
 * Guardrails for NRQL queries to prevent excessive data retrieval.
 */
public final class NrqlGuardrails {

    private static final Set<String> ALLOWED_METRICS = Set.of(
            "errors", "latency", "throughput", "apdex", "error_rate",
            "default", "latency.transaction.rate"
    );
    private static final Set<String> ALLOWED_GROUP_BY = Set.of(
            "", "appName", "entity.guid", "host", "service", "endpoint"
    );

    private static final int DEFAULT_DAYS = 30;
    private static final int HOURS_PER_DAY = 24;
    private static final int MINUTES_PER_HOUR = 60;
    private static final int DEFAULT_MAX_LIMIT = 10_000;
    private static final int DEFAULT_MAX_FILTERS = 5;

    private final int maxTimeWindowMinutes;
    private final int maxLimit;
    private final int maxFilters;

    /**
     * Create guardrails with specific limits.
     * @param maxWindow the maximum time window in minutes
     * @param maxLimitVal the maximum results limit
     * @param maxFiltersVal the maximum number of filters
     */
    public NrqlGuardrails(final int maxWindow, final int maxLimitVal, final int maxFiltersVal) {
        this.maxTimeWindowMinutes = maxWindow;
        this.maxLimit = maxLimitVal;
        this.maxFilters = maxFiltersVal;
    }

    /**
     * @return default guardrails.
     */
    public static NrqlGuardrails defaults() {
        return new NrqlGuardrails(DEFAULT_DAYS * HOURS_PER_DAY * MINUTES_PER_HOUR,
                DEFAULT_MAX_LIMIT, DEFAULT_MAX_FILTERS);
    }

    /**
     * @param metric metric name
     * @return true if allowed
     */
    public boolean isMetricAllowed(final String metric) {
        return ALLOWED_METRICS.contains(metric);
    }

    /**
     * @param groupBy group by field
     * @return true if allowed
     */
    public boolean isGroupByAllowed(final String groupBy) {
        return ALLOWED_GROUP_BY.contains(groupBy);
    }

    /** @return max time window. */
    public int maxTimeWindowMinutes() {
        return maxTimeWindowMinutes;
    }

    /** @return max limit. */
    public int maxLimit() {
        return maxLimit;
    }

    /** @return max filters. */
    public int maxFilters() {
        return maxFilters;
    }
}
