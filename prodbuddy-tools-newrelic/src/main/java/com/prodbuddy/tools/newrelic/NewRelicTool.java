package com.prodbuddy.tools.newrelic;

import java.util.Map;
import java.util.Set;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

public final class NewRelicTool implements Tool {

    private static final String NAME = "newrelic";
    private final NewRelicScenarioCatalog catalog;
    private final NrqlQueryBuilder queryBuilder;
    private final NrqlQueryValidator validator;
    private final NrqlGraphQLClient client;
    private final SequenceLogger seqLog;

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
        this.seqLog = new Slf4jSequenceLogger(NewRelicTool.class);
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                NAME,
                "New Relic data tool",
                Set.of("newrelic.scenario", "newrelic.query", "newrelic.query_metrics", "newrelic.validate", "newrelic.list_dashboards", "newrelic.get_dashboard", "newrelic.list_apps", "newrelic.list_external_services", "newrelic.get_trace", "newrelic.gql_query")
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        return "newrelic".equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        seqLog.logSequence("AgentLoopOrchestrator", "newrelic", "execute", "Executing NewRelic " + request.operation());
        try {
            return dispatch(request, context);
        } catch (Exception ex) {
            seqLog.logSequence("newrelic", "AgentLoopOrchestrator", "execute", "Failed: " + ex.getMessage());
            return ToolResponse.failure("NEWRELIC_SAFE_CATCH", "Unexpected error in New Relic tool: " + ex.getMessage());
        }
    }

    private ToolResponse dispatch(ToolRequest request, ToolContext context) {
        String operation = request.operation().toLowerCase();
        return switch (operation) {
            case "scenarios" -> listScenarios();
            case "validate" -> validate(request);
            case "query_metrics", "query" -> runQuery(requestFrom(request), context);
            case "list_dashboards" -> listDashboards(dashboardRequestFrom(request), context);
            case "get_dashboard" -> getDashboard(dashboardRequestFrom(request), context);
            case "list_apps" -> listApplications(request, context);
            case "list_external_services" -> listExternalServices(request, context);
            case "get_trace" -> getTrace(traceRequestFrom(request), context);
            case "gql_query" -> client.query(String.valueOf(request.payload().getOrDefault("graphqlBody", "")), context);
            default -> ToolResponse.failure("NEWRELIC_OPERATION", "supported ops: scenarios, query, list_dashboards, get_dashboard, get_trace, etc.");
        };
    }

    private ToolResponse validate(ToolRequest request) {
        NrqlQueryRequest qr = requestFrom(request);
        ToolResponse v = validator.validate(qr);
        return v == null ? ToolResponse.ok(Map.of("valid", true)) : v;
    }

    private ToolResponse listScenarios() {
        seqLog.logSequence("newrelic", "AgentLoopOrchestrator", "execute", "Listing scenarios");
        return ToolResponse.ok(Map.of("scenarios", catalog.supported(), "preferredOperation", "query_metrics"));
    }

    private ToolResponse listDashboards(DashboardRequest request, ToolContext context) {
        String query = "{ actor { entitySearch(query: \\\"type = 'DASHBOARD'"
                + (request.name().isBlank() ? "" : " AND name LIKE '%" + request.name() + "%'")
                + "\\\") { results { entities { guid name } } } } }";
        return client.query("{\"query\":\"" + query + "\"}", context);
    }

    private ToolResponse getDashboard(DashboardRequest request, ToolContext context) {
        if (request.guid().isBlank()) {
            return ToolResponse.failure("NEWRELIC_DASHBOARD_GUID", "guid is required for get_dashboard");
        }
        String query = "{ actor { entity(guid: \\\"" + request.guid() + "\\\") { name ... on DashboardEntity { pages { name widgets { title visualization { id } rawConfiguration } } } } } }";
        return client.query("{\"query\":\"" + query + "\"}", context);
    }

    private ToolResponse listApplications(ToolRequest request, ToolContext context) {
        String name = String.valueOf(request.payload().getOrDefault("name", ""));
        String query = "{ actor { entitySearch(query: \\\"type = 'APPLICATION' "
                + (name.isBlank() ? "" : "AND name LIKE '%" + name + "%' ")
                + "\\\") { results { entities { guid name } } } } }";
        return client.query("{\"query\":\"" + query + "\"}", context);
    }

    private ToolResponse listExternalServices(ToolRequest request, ToolContext context) {
        String accountId = String.valueOf(request.payload().getOrDefault("accountId", context.envOrDefault("NEWRELIC_ACCOUNT_ID", "")));
        String querySnippet = accountId.isBlank() ? "type = 'EXT'" : "type = 'EXT' AND accountId = " + accountId;
        String query = "{ actor { entitySearch(query: \\\"" + querySnippet + "\\\") { results { entities { guid name } } } } }";
        return client.query("{\"query\":\"" + query + "\"}", context);
    }

    private ToolResponse getTrace(TraceRequest request, ToolContext context) {
        if (request.traceId().isBlank()) {
            return ToolResponse.failure("NEWRELIC_TRACE_ID", "traceId is required for get_trace");
        }
        String query = "{ actor { distributedTracing { trace(id: \\\"" + request.traceId() + "\\\") { spans { id parentSpanId serviceName operationName durationMs timestamp } } } } }";
        return client.query("{\"query\":\"" + query + "\"}", context);
    }

    private TraceRequest traceRequestFrom(ToolRequest request) {
        return new TraceRequest(String.valueOf(request.payload().getOrDefault("traceId", "")));
    }

    private DashboardRequest dashboardRequestFrom(ToolRequest request) {
        String guid = String.valueOf(request.payload().getOrDefault("guid", ""));
        String name = String.valueOf(request.payload().getOrDefault("name", ""));
        return new DashboardRequest(guid, name);
    }

    private ToolResponse runQuery(NrqlQueryRequest queryRequest, ToolContext context) {
        ToolResponse validation = validator.validate(queryRequest);
        if (validation != null) {
            seqLog.logSequence("newrelic", "AgentLoopOrchestrator", "runQuery", "Validation failed");
            return validation;
        }

        String nrql = queryBuilder.build(queryRequest);
        seqLog.logSequence("newrelic", "NewRelicAPI", "runQuery", "Executing NRQL query");
        ToolResponse result = client.execute(nrql, context);
        seqLog.logSequence("NewRelicAPI", "newrelic", "runQuery", "Query complete: " + result.success());
        return result;
    }

    private NrqlQueryRequest requestFrom(ToolRequest request) {
        Object m = request.payload().get("metric");
        if (m == null) m = request.payload().get("metric_name");
        if (m == null) m = request.payload().get("scenario");
        
        String metric = (m != null) ? String.valueOf(m) : "default";
        
        int window = intFrom(request.payload(), "timeWindowMinutes", 5);
        if (request.payload().containsKey("time_window")) {
            // Basic parsing for "last X hours" or "X"
            String tw = String.valueOf(request.payload().get("time_window")).toLowerCase();
            if (tw.contains("hour")) {
                window = Integer.parseInt(tw.replaceAll("[^0-9]", "")) * 60;
            } else {
                try { window = Integer.parseInt(tw.replaceAll("[^0-9]", "")); } catch (Exception e) {}
            }
        }

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
