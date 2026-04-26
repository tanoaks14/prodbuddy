package com.prodbuddy.tools.newrelic;

import com.prodbuddy.core.tool.ToolResponse;

import java.util.Map;

/**
 * Validator for NRQL query requests using guardrails.
 */
public final class NrqlQueryValidator {

    /** Guardrails to check against. */
    private final NrqlGuardrails guardrails;

    /**
     * Create a validator.
     * @param guardrailsVal guardrails to use
     */
    public NrqlQueryValidator(final NrqlGuardrails guardrailsVal) {
        this.guardrails = guardrailsVal;
    }

    /**
     * Validates a request.
     * @param request the request to validate
     * @return null if valid, ToolResponse if invalid
     */
    public ToolResponse validate(final NrqlQueryRequest request) {
        ToolResponse metricCheck = validateMetric(request.metric());
        if (metricCheck != null) {
            return metricCheck;
        }

        ToolResponse windowCheck = validateWindow(request.timeWindowMinutes());
        if (windowCheck != null) {
            return windowCheck;
        }

        ToolResponse limitCheck = validateLimit(request.limit());
        if (limitCheck != null) {
            return limitCheck;
        }

        ToolResponse filtersCheck = validateFilters(request.filters());
        if (filtersCheck != null) {
            return filtersCheck;
        }

        return validateGroupBy(request.groupBy());
    }

    private ToolResponse validateMetric(final String metric) {
        if (guardrails.isMetricAllowed(metric)) {
            return null;
        }
        return ToolResponse.failure("NEWRELIC_METRIC",
            "Unsupported metric: " + metric);
    }

    private ToolResponse validateWindow(final int minutes) {
        if (minutes <= guardrails.maxTimeWindowMinutes()) {
            return null;
        }
        return ToolResponse.failure("NEWRELIC_WINDOW",
            "timeWindowMinutes exceeds guardrail");
    }

    private ToolResponse validateLimit(final int limit) {
        if (limit <= guardrails.maxLimit()) {
            return null;
        }
        return ToolResponse.failure("NEWRELIC_LIMIT",
            "limit exceeds guardrail");
    }

    private ToolResponse validateFilters(final Map<String, String> filters) {
        if (filters.size() <= guardrails.maxFilters()) {
            return null;
        }
        return ToolResponse.failure("NEWRELIC_FILTERS",
            "too many filters requested");
    }

    private ToolResponse validateGroupBy(final String groupBy) {
        if (guardrails.isGroupByAllowed(groupBy)) {
            return null;
        }
        return ToolResponse.failure("NEWRELIC_GROUP",
            "Unsupported groupBy: " + groupBy);
    }
}
