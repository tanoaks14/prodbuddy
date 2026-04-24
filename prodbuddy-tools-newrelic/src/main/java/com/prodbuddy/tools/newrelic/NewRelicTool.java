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
                Set.of("newrelic.scenario", "newrelic.query", "newrelic.query_metrics",
                        "newrelic.validate", "newrelic.list_dashboards", "newrelic.get_dashboard",
                        "newrelic.list_apps", "newrelic.list_external_services",
                        "newrelic.get_trace", "newrelic.gql_query", "newrelic.snapshot")
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

    private ToolResponse dispatch(final ToolRequest request, final ToolContext context) {
        String operation = request.operation().toLowerCase();
        
        ToolResponse sys = dispatchSystem(operation, request, context);
        if (sys != null) return sys;
        
        ToolResponse ent = dispatchEntity(operation, request, context);
        if (ent != null) return ent;
        
        return dispatchTelemetry(operation, request, context);
    }

    private ToolResponse dispatchSystem(final String operation, final ToolRequest request, final ToolContext context) {
        return switch (operation) {
            case "scenarios" -> listScenarios();
            case "validate" -> validate(request);
            case "gql_query" -> client.query(String.valueOf(request.payload().getOrDefault("graphqlBody", "")), context);
            default -> null;
        };
    }

    private ToolResponse dispatchEntity(final String operation, final ToolRequest request, final ToolContext context) {
        return switch (operation) {
            case "list_dashboards" -> listDashboards(dashboardRequestFrom(request), context);
            case "get_dashboard" -> getDashboard(dashboardRequestFrom(request), context);
            case "list_apps" -> listApplications(request, context);
            case "list_external_services" -> listExternalServices(request, context);
            case "snapshot" -> createSnapshot(request, context);
            default -> null;
        };
    }

    private ToolResponse dispatchTelemetry(final String operation, final ToolRequest request, final ToolContext context) {
        return switch (operation) {
            case "query_metrics", "query" -> runQuery(requestFrom(request), context);
            case "get_trace" -> getTrace(traceRequestFrom(request), context);
            default -> ToolResponse.failure("NEWRELIC_OPERATION", "supported ops: scenarios, query, list_dashboards, get_dashboard, get_trace, snapshot, etc.");
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

    private static final int DEFAULT_TIME_WINDOW = 5;
    private static final int MINUTES_PER_HOUR = 60;
    private static final int DEFAULT_LIMIT = 100;

    private ToolResponse getTrace(final TraceRequest request, final ToolContext context) {
        if (request.traceId().isBlank()) {
            return ToolResponse.failure("NEWRELIC_TRACE_ID",
                "traceId is required for get_trace");
        }
        String query = "{ actor { distributedTracing { trace(id: \\\""
            + request.traceId() + "\\\") { spans { id parentSpanId serviceName "
            + "operationName durationMs timestamp } } } } }";
        return client.query("{\"query\":\"" + query + "\"}", context);
    }

    private ToolResponse createSnapshot(final ToolRequest request,
                                        final ToolContext context) {
        String guid = String.valueOf(request.payload().getOrDefault("guid", ""));
        if (guid.isBlank()) {
            return ToolResponse.failure("NEWRELIC_SNAPSHOT_GUID",
                "guid is required for snapshot (use a dashboard page GUID)");
        }
        String query = "mutation { dashboardCreateSnapshotUrl(guid: \\\""
            + guid + "\\\") }";
        ToolResponse res = client.query("{\"query\":\"" + query + "\"}", context);
        if (!res.success()) {
            return res;
        }
        String body = String.valueOf(((Map<String, Object>) res.data()).get("body"));
        return parseSnapshotResponse(body);
    }

    private ToolResponse parseSnapshotResponse(final String body) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(body);
            if (node.has("errors")) {
                return ToolResponse.failure("NEWRELIC_GQL_ERROR",
                    node.get("errors").toString());
            }
            String url = node.get("data").get("dashboardCreateSnapshotUrl").asText();
            if (url == null || url.equals("null")) {
                return ToolResponse.failure("NEWRELIC_SNAPSHOT_NULL",
                    "Snapshot URL was null. Ensure the GUID is a dashboard PAGE GUID.");
            }
            return ToolResponse.ok(Map.of("url", url, "body", url));
        } catch (Exception e) {
            return ToolResponse.failure("NEWRELIC_SNAPSHOT_PARSE",
                "Failed to parse snapshot response: " + e.getMessage());
        }
    }

    private TraceRequest traceRequestFrom(final ToolRequest request) {
        return new TraceRequest(String.valueOf(
            request.payload().getOrDefault("traceId", "")));
    }

    private DashboardRequest dashboardRequestFrom(final ToolRequest request) {
        String guid = String.valueOf(request.payload().getOrDefault("guid", ""));
        String name = String.valueOf(request.payload().getOrDefault("name", ""));
        return new DashboardRequest(guid, name);
    }

    private ToolResponse runQuery(final NrqlQueryRequest queryRequest, final ToolContext context) {
        ToolResponse validation = validator.validate(queryRequest);
        if (validation != null) {
            seqLog.logSequence("newrelic", "AgentLoopOrchestrator",
                "runQuery", "Validation failed");
            return validation;
        }

        String nrql = queryBuilder.build(queryRequest);
        seqLog.logSequence("newrelic", "NewRelicAPI", "runQuery",
            "Executing NRQL query");
        ToolResponse result = client.execute(nrql, context);
        seqLog.logSequence("NewRelicAPI", "newrelic", "runQuery",
            "Query complete: " + result.success());
        return result;
    }

    private NrqlQueryRequest requestFrom(final ToolRequest request) {
        String metric = parseMetric(request.payload());
        int window = parseTimeWindow(request.payload());
        int limit = intFrom(request.payload(), "limit", DEFAULT_LIMIT);
        String groupBy = String.valueOf(request.payload()
            .getOrDefault("groupBy", ""));
        Map<String, String> filters = filtersFrom(request.payload()
            .get("filters"));
        return new NrqlQueryRequest(metric, filters, window, limit, groupBy);
    }

    private String parseMetric(final Map<String, Object> payload) {
        Object m = payload.get("metric");
        if (m == null) {
            m = payload.get("metric_name");
        }
        if (m == null) {
            m = payload.get("scenario");
        }
        return (m != null) ? String.valueOf(m) : "default";
    }

    private int parseTimeWindow(final Map<String, Object> payload) {
        int window = intFrom(payload, "timeWindowMinutes", DEFAULT_TIME_WINDOW);
        if (payload.containsKey("time_window")) {
            String tw = String.valueOf(payload.get("time_window")).toLowerCase();
            if (tw.contains("hour")) {
                window = Integer.parseInt(tw.replaceAll("[^0-9]", ""))
                    * MINUTES_PER_HOUR;
            } else {
                try {
                    window = Integer.parseInt(tw.replaceAll("[^0-9]", ""));
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        return window;
    }


    @SuppressWarnings("unchecked")
    private Map<String, String> filtersFrom(final Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, String>) map;
        }
        return Map.of();
    }

    private int intFrom(final Map<String, Object> payload, final String key, final int defaultValue) {
        Object value = payload.getOrDefault(key, defaultValue);
        return Integer.parseInt(String.valueOf(value));
    }
}
