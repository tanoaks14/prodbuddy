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
                        "newrelic.get_dashboard_data",
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
            case "get_dashboard_data" -> getDashboardData(dashboardRequestFrom(request), context);
            case "list_apps" -> listApplications(request, context);
            case "list_external_services" -> listExternalServices(request, context);
            case "snapshot" -> createSnapshot(request, context);
            default -> null;
        };
    }

    private ToolResponse getDashboardData(DashboardRequest req, ToolContext ctx) {
        ToolResponse dash = getDashboard(req, ctx);
        if (!dash.success()) return dash;
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(String.valueOf(dash.data().get("body")));
            com.fasterxml.jackson.databind.JsonNode pages = root.path("data").path("actor").path("entity").path("pages");
            com.fasterxml.jackson.databind.JsonNode selectedPage = pages.path(0);
            if (!req.pageGuid().isEmpty()) {
                for (com.fasterxml.jackson.databind.JsonNode p : pages) {
                    if (req.pageGuid().equals(p.path("guid").asText())) {
                        selectedPage = p;
                        break;
                    }
                }
            }
            com.fasterxml.jackson.databind.JsonNode widgets = selectedPage.path("widgets");
            return ToolResponse.ok(Map.of("dashboard", req.guid(), "results", processWidgets(widgets, req, ctx)));
        } catch (Exception e) { return ToolResponse.failure("DASHBOARD_DATA_ERROR", e.getMessage()); }
    }

    private java.util.List<Map<String, Object>> processWidgets(com.fasterxml.jackson.databind.JsonNode ws, DashboardRequest req, ToolContext ctx) throws Exception {
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(ws.size(), 10); i++) {
            com.fasterxml.jackson.databind.JsonNode w = ws.get(i);
            String rc = w.path("rawConfiguration").asText();
            if (rc != null && rc.contains("nrqlQueries")) {
                String n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(rc).path("nrqlQueries").path(0).path("query").asText();
                if (!n.isEmpty()) {
                    if (!req.compareWith().isEmpty() && !n.toLowerCase().contains("compare with")) n += " COMPARE WITH " + req.compareWith();
                    results.add(Map.of("title", w.path("title").asText(), "query", n, "data", client.execute(n, ctx).data()));
                }
            }
        }
        return results;
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
        if (request.guid().isBlank()) return ToolResponse.failure("NEWRELIC_DASHBOARD_GUID", "guid required");
        String query = "{ actor { entity(guid: \\\"" + request.guid() + "\\\") { name "
                + "... on DashboardEntity { pages { name guid widgets { title visualization { id } rawConfiguration } } } } } }";
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

    private ToolResponse createSnapshot(final ToolRequest req, final ToolContext ctx) {
        String guid = String.valueOf(req.payload().getOrDefault("guid", ""));
        String format = String.valueOf(req.payload().getOrDefault("format", "PDF")).toUpperCase();
        if (guid.isBlank()) {
            return ToolResponse.failure("NEWRELIC_SNAPSHOT_GUID", "guid required");
        }
        String q = "mutation { dashboardCreateSnapshotUrl(guid: \\\"" + guid + "\\\"" + buildSnapshotParams(req) + ") }";
        ToolResponse res = client.query("{\"query\":\"" + q + "\"}", ctx);
        if (!res.success()) return res;
        ToolResponse parsed = parseSnapshotResponse(String.valueOf(((Map<String, Object>) res.data()).get("body")));
        if (parsed.success()) {
            String url = String.valueOf(parsed.data().get("url"));
            String finalUrl = url.replace("format=PDF", "format=" + format) + buildUrlParams(req);
            return ToolResponse.ok(Map.of("url", finalUrl, "body", finalUrl));
        }
        return parsed;
    }

    private String buildUrlParams(ToolRequest req) {
        Object p = req.payload().get("params");
        if (!(p instanceof Map<?, ?> map)) return "";
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> sb.append("&").append(k).append("=").append(v));
        return sb.toString();
    }

    private String buildSnapshotParams(ToolRequest req) {
        Object d = req.payload().get("duration");
        return (d == null) ? "" : ", params: { timeWindow: { duration: " + (Long.parseLong(String.valueOf(d)) * 60000L) + " } }";
    }

    private ToolResponse parseSnapshotResponse(final String body) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            if (node.has("errors")) return ToolResponse.failure("NEWRELIC_GQL_ERROR", node.get("errors").toString());
            String url = node.get("data").get("dashboardCreateSnapshotUrl").asText();
            if (url == null || url.equals("null")) return ToolResponse.failure("NEWRELIC_SNAPSHOT_NULL", "Snapshot URL was null.");
            return ToolResponse.ok(Map.of("url", url, "body", url));
        } catch (Exception e) {
            return ToolResponse.failure("NEWRELIC_SNAPSHOT_PARSE", "Parse failed: " + e.getMessage());
        }
    }

    private TraceRequest traceRequestFrom(final ToolRequest request) {
        return new TraceRequest(String.valueOf(
            request.payload().getOrDefault("traceId", "")));
    }

    private DashboardRequest dashboardRequestFrom(final ToolRequest request) {
        Map<String, Object> p = request.payload();
        return new DashboardRequest(String.valueOf(p.getOrDefault("guid", "")),
                                    String.valueOf(p.getOrDefault("name", "")),
                                    String.valueOf(p.getOrDefault("compareWith", "")),
                                    String.valueOf(p.getOrDefault("pageGuid", "")));
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
        Map<String, Object> p = request.payload();
        String m = String.valueOf(p.getOrDefault("metric", p.getOrDefault("metric_name", p.getOrDefault("scenario", "default"))));
        int w = intFrom(p, "timeWindowMinutes", DEFAULT_TIME_WINDOW);
        if (p.containsKey("time_window")) {
            String tw = String.valueOf(p.get("time_window")).toLowerCase();
            if (tw.contains("hour")) w = Integer.parseInt(tw.replaceAll("[^0-9]", "")) * MINUTES_PER_HOUR;
            else try { w = Integer.parseInt(tw.replaceAll("[^0-9]", "")); } catch (Exception e) { /* ignore */ }
        }
        return new NrqlQueryRequest(m, filtersFrom(p.get("filters")), w, intFrom(p, "limit", DEFAULT_LIMIT), String.valueOf(p.getOrDefault("groupBy", "")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> filtersFrom(final Object value) {
        return (value instanceof Map) ? (Map<String, String>) value : Map.of();
    }

    private int intFrom(final Map<String, Object> p, final String k, final int d) {
        return Integer.parseInt(String.valueOf(p.getOrDefault(k, d)));
    }
}
