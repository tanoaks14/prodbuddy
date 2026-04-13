package com.prodbuddy.tools.newrelic;

import java.util.Map;

import com.prodbuddy.core.tool.ToolResponse;

public final class NrqlQueryValidator {

    private final NrqlGuardrails guardrails;

    public NrqlQueryValidator(NrqlGuardrails guardrails) {
        this.guardrails = guardrails;
    }

    public ToolResponse validate(NrqlQueryRequest request) {
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

    private ToolResponse validateMetric(String metric) {
        if (guardrails.isMetricAllowed(metric)) {
            return null;
        }
        return ToolResponse.failure("NEWRELIC_METRIC", "Unsupported metric: " + metric);
    }

    private ToolResponse validateWindow(int minutes) {
        if (minutes <= guardrails.maxTimeWindowMinutes()) {
            return null;
        }
        return ToolResponse.failure("NEWRELIC_WINDOW", "timeWindowMinutes exceeds guardrail");
    }

    private ToolResponse validateLimit(int limit) {
        if (limit <= guardrails.maxLimit()) {
            return null;
        }
        return ToolResponse.failure("NEWRELIC_LIMIT", "limit exceeds guardrail");
    }

    private ToolResponse validateFilters(Map<String, String> filters) {
        if (filters.size() <= guardrails.maxFilters()) {
            return null;
        }
        return ToolResponse.failure("NEWRELIC_FILTERS", "too many filters requested");
    }

    private ToolResponse validateGroupBy(String groupBy) {
        if (guardrails.isGroupByAllowed(groupBy)) {
            return null;
        }
        return ToolResponse.failure("NEWRELIC_GROUP", "Unsupported groupBy: " + groupBy);
    }
}
