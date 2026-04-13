package com.prodbuddy.tools.newrelic;

import java.util.Map;
import java.util.Set;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

public final class NewRelicTool implements Tool {

    private static final String NAME = "newrelic";
    private final NewRelicScenarioCatalog catalog;
    private final NrqlQueryBuilder queryBuilder;
    private final NrqlQueryValidator validator;
    private final NrqlGraphQLClient client;

    public NewRelicTool(NewRelicScenarioCatalog catalog) {
        this(catalog, new NrqlQueryBuilder(), new NrqlQueryValidator(NrqlGuardrails.defaults()), new NrqlGraphQLClient());
    }

    public NewRelicTool(
            NewRelicScenarioCatalog catalog,
            NrqlQueryBuilder queryBuilder,
            NrqlQueryValidator validator,
            NrqlGraphQLClient client
    ) {
        this.catalog = catalog;
        this.queryBuilder = queryBuilder;
        this.validator = validator;
        this.client = client;
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                NAME,
                "New Relic data tool",
                Set.of("newrelic.scenario", "newrelic.query", "newrelic.query_metrics", "newrelic.validate")
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        return "newrelic".equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        String operation = request.operation().toLowerCase();
        if ("scenarios".equals(operation)) {
            return ToolResponse.ok(Map.of("scenarios", catalog.supported(), "preferredOperation", "query_metrics"));
        }

        NrqlQueryRequest queryRequest = requestFrom(request);
        if ("validate".equals(operation)) {
            ToolResponse validation = validator.validate(queryRequest);
            return validation == null ? ToolResponse.ok(Map.of("valid", true)) : validation;
        }

        if ("query_metrics".equals(operation) || "query".equals(operation)) {
            return runQuery(queryRequest, context);
        }

        return ToolResponse.failure("NEWRELIC_OPERATION", "supported operations: scenarios, query_metrics, query, validate");
    }

    private ToolResponse runQuery(NrqlQueryRequest queryRequest, ToolContext context) {
        ToolResponse validation = validator.validate(queryRequest);
        if (validation != null) {
            return validation;
        }

        String nrql = queryBuilder.build(queryRequest);
        return client.execute(nrql, context);
    }

    private NrqlQueryRequest requestFrom(ToolRequest request) {
        String scenario = String.valueOf(request.payload().getOrDefault("scenario", "default"));
        String metric = String.valueOf(request.payload().getOrDefault("metric", scenario));
        int window = intFrom(request.payload(), "timeWindowMinutes", 5);
        int limit = intFrom(request.payload(), "limit", 100);
        String groupBy = String.valueOf(request.payload().getOrDefault("groupBy", ""));
        Map<String, String> filters = filtersFrom(request.payload().get("filters"));
        return new NrqlQueryRequest(metric, filters, window, limit, groupBy);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> filtersFrom(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, String>) map;
        }
        return Map.of();
    }

    private int intFrom(Map<String, Object> payload, String key, int defaultValue) {
        Object value = payload.getOrDefault(key, defaultValue);
        return Integer.parseInt(String.valueOf(value));
    }
}
