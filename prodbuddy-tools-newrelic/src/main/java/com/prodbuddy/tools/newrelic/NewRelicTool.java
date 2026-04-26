package com.prodbuddy.tools.newrelic;

import com.prodbuddy.core.system.QueryService;
import com.prodbuddy.core.tool.*;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

import java.util.Map;
import java.util.Set;

public final class NewRelicTool implements Tool {

    private static final String NAME = "newrelic";
    private final NewRelicScenarioCatalog catalog;
    private final NrqlQueryBuilder queryBuilder;
    private final NrqlQueryValidator validator;
    private final NrqlGraphQLClient client;
    private final DashboardDataService dataService;
    private final QueryService queryService;
    private final SequenceLogger seqLog;

    public NewRelicTool(final NewRelicScenarioCatalog catalog) {
        this(catalog, new Slf4jSequenceLogger(NewRelicTool.class));
    }

    public NewRelicTool(final NewRelicScenarioCatalog catalog,
                        final SequenceLogger seqLog) {
        this.seqLog = seqLog;
        this.catalog = catalog;
        this.queryBuilder = new NrqlQueryBuilder();
        this.validator = new NrqlQueryValidator(NrqlGuardrails.defaults());
        this.client = new NrqlGraphQLClient(seqLog, styling());
        this.queryService = new QueryService();
        this.dataService = new DashboardDataService(client, seqLog);
    }

    public NewRelicTool(final NewRelicScenarioCatalog c,
                        final NrqlQueryBuilder b,
                        final NrqlQueryValidator v,
                        final NrqlGraphQLClient cl,
                        final QueryService qs,
                        final SequenceLogger log) {
        this.catalog = c;
        this.queryBuilder = b;
        this.validator = v;
        this.client = cl;
        this.queryService = qs;
        this.seqLog = log;
        this.dataService = new DashboardDataService(cl, log);
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
    public ToolStyling styling() {
        return new ToolStyling("#B2DFDB", "#004D40", "#E0F2F1", "☁️ New Relic", java.util.Map.of());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        seqLog.logSequence("AgentLoopOrchestrator", "NewRelic", "execute", "Executing NewRelic " + request.operation(), styling().toMetadata("NewRelic"));
        try {
            return dispatch(request, context);
        } catch (Exception ex) {
            seqLog.logSequence("NewRelic", "AgentLoopOrchestrator", "execute", "Failed: " + ex.getMessage(), styling().toMetadata("NewRelic"));
            return ToolResponse.failure("NEWRELIC_SAFE_CATCH", "Unexpected error in New Relic tool: " + ex.getMessage());
        }
    }

    private ToolResponse dispatch(final ToolRequest request, final ToolContext context) {
        String op = request.operation().toLowerCase();
        ToolResponse res = dispatchSystem(op, request, context);
        return res != null ? res : dispatchEntity(op, request, context);
    }

    private ToolResponse dispatchSystem(String op, ToolRequest req, ToolContext ctx) {
        return switch (op) {
            case "scenarios" -> listScenarios();
            case "validate" -> validate(req);
            case "gql_query" -> client.query(String.valueOf(req.payload().getOrDefault("graphqlBody", "")), ctx);
            case "query_metrics", "query" -> runQuery(requestFrom(req), ctx);
            case "get_trace" -> getTrace(traceRequestFrom(req), ctx);
            default -> null;
        };
    }

    private ToolResponse dispatchEntity(String op, ToolRequest req, ToolContext ctx) {
        return switch (op) {
            case "list_dashboards" -> listDashboards(dashboardRequestFrom(req), ctx);
            case "get_dashboard" -> getDashboard(dashboardRequestFrom(req), ctx);
            case "get_dashboard_data" -> {
                DashboardRequest dr = dashboardRequestFrom(req);
                ToolResponse d = getDashboard(dr, ctx);
                yield d.success() ? dataService.getDashboardData(dr, d, ctx) : d;
            }
            case "list_apps" -> listApplications(req, ctx);
            case "list_external_services" -> listExternalServices(req, ctx);
            case "snapshot" -> createSnapshot(req, ctx);
            default -> ToolResponse.failure("NEWRELIC_OPERATION", "unsupported: " + op);
        };
    }

    private ToolResponse getDashboard(DashboardRequest request, ToolContext context) {
        if (request.guid().isBlank()) {
            return ToolResponse.failure("NEWRELIC_DASHBOARD_GUID", "guid required");
        }
        String query;
        if (queryService.exists("newrelic/get_dashboard.graphql")) {
            query = queryService.render("newrelic/get_dashboard.graphql",
                    Map.of("guid", request.guid()));
        } else {
            query = "{ actor { entity(guid: \\\"" + request.guid() + "\\\") { name "
                + "... on DashboardEntity { pages { name guid widgets { title "
                + "visualization { id } rawConfiguration } } } } } } }";
        }
        String escaped = query.replace("\n", " ")
                              .replace("\"", "\\\"");
        return client.query("{\"query\":\"" + escaped + "\"}", context);
    }

    private ToolResponse validate(ToolRequest request) {
        NrqlQueryRequest qr = requestFrom(request);
        ToolResponse v = validator.validate(qr);
        return v == null ? ToolResponse.ok(Map.of("valid", true)) : v;
    }

    private ToolResponse listScenarios() {
        seqLog.logSequence("NewRelic", "AgentLoopOrchestrator", "execute", "Listing scenarios", styling().toMetadata("NewRelic"));
        return ToolResponse.ok(Map.of("scenarios", catalog.supported(), "preferredOperation", "query_metrics"));
    }

    private ToolResponse listDashboards(DashboardRequest request, ToolContext context) {
        String nameFilter = request.name().isBlank() ? "" : " AND name LIKE '%" + request.name() + "%'";
        String query;
        if (queryService.exists("newrelic/list_dashboards.graphql")) {
            query = queryService.render("newrelic/list_dashboards.graphql", Map.of("nameFilter", nameFilter));
        } else {
            query = "{ actor { entitySearch(query: \\\"type = 'DASHBOARD'" + nameFilter + "\\\") { results { entities { guid name } } } } }";
        }
        return client.query("{\"query\":\"" + query.replace("\n", " ").replace("\"", "\\\"") + "\"}", context);
    }

    private ToolResponse listApplications(ToolRequest request, ToolContext context) {
        String name = String.valueOf(request.payload().getOrDefault("name", ""));
        String nameFilter = name.isBlank() ? "" : "AND name LIKE '%" + name + "%' ";
        String query;
        if (queryService.exists("newrelic/list_apps.graphql")) {
            query = queryService.render("newrelic/list_apps.graphql", Map.of("nameFilter", nameFilter));
        } else {
            query = "{ actor { entitySearch(query: \\\"type = 'APPLICATION' " + nameFilter + "\\\") { results { entities { guid name } } } } }";
        }
        return client.query("{\"query\":\"" + query.replace("\n", " ").replace("\"", "\\\"") + "\"}", context);
    }

    private ToolResponse listExternalServices(ToolRequest request, ToolContext context) {
        String accountId = String.valueOf(request.payload().getOrDefault("accountId", context.envOrDefault("NEWRELIC_ACCOUNT_ID", "")));
        String querySnippet = accountId.isBlank() ? "type = 'EXT'" : "type = 'EXT' AND accountId = " + accountId;
        String query;
        if (queryService.exists("newrelic/list_external_services.graphql")) {
            query = queryService.render("newrelic/list_external_services.graphql", Map.of("querySnippet", querySnippet));
        } else {
            query = "{ actor { entitySearch(query: \\\"" + querySnippet + "\\\") { results { entities { guid name } } } } }";
        }
        return client.query("{\"query\":\"" + query.replace("\n", " ").replace("\"", "\\\"") + "\"}", context);
    }

    private static final int DEFAULT_TIME_WINDOW = 5;
    private static final int DEFAULT_LIMIT = 100;

    private ToolResponse getTrace(final TraceRequest request, final ToolContext context) {
        if (request.traceId().isBlank()) return ToolResponse.failure("NEWRELIC_TRACE_ID", "traceId required");
        String q = "{ actor { account(id: " + context.env("NEWRELIC_ACCOUNT_ID") + ") { nrql(query: \\\"SELECT * FROM Span WHERE trace.id = '" + request.traceId() + "' LIMIT 100\\\") { results } } } }";
        return client.query("{\"query\":\"" + q + "\"}", context);
    }

    private ToolResponse createSnapshot(ToolRequest req, ToolContext ctx) {
        String guid = String.valueOf(req.payload().get("guid"));
        String format = String.valueOf(req.payload().getOrDefault("format", "PNG")).toUpperCase();
        if (guid == null || guid.isBlank() || guid.equals("null")) return ToolResponse.failure("NEWRELIC_SNAPSHOT_GUID", "guid required");
        String q = "mutation { dashboardCreateSnapshotUrl(guid: \\\"" + guid + "\\\"" + buildSnapshotParams(req) + ") }";
        ToolResponse res = client.query("{\"query\":\"" + q + "\"}", ctx);
        if (!res.success()) return res;
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(String.valueOf(((Map<String, Object>) res.data()).get("body")));
            if (node.has("errors")) return ToolResponse.failure("NEWRELIC_GQL_ERROR", node.get("errors").toString());
            String url = node.get("data").get("dashboardCreateSnapshotUrl").asText();
            if (url == null || url.equals("null")) return ToolResponse.failure("NEWRELIC_SNAPSHOT_NULL", "Snapshot URL null");
            String finalUrl = url.replace("format=PDF", "format=" + format) + buildUrlParams(req);
            return ToolResponse.ok(Map.of("url", finalUrl, "body", finalUrl));
        } catch (Exception e) { return ToolResponse.failure("NEWRELIC_SNAPSHOT_PARSE", e.getMessage()); }
    }

    private String buildSnapshotParams(ToolRequest req) {
        Object d = req.payload().get("duration");
        return (d == null) ? "" : ", params: { timeWindow: { duration: " + (Long.parseLong(String.valueOf(d)) * 60000L) + " } }";
    }

    private String buildUrlParams(ToolRequest req) {
        Object p = req.payload().get("params");
        if (!(p instanceof Map<?, ?> map)) return "";
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> sb.append("&").append(k).append("=").append(v));
        return sb.toString();
    }

    private TraceRequest traceRequestFrom(final ToolRequest request) {
        return new TraceRequest(String.valueOf(request.payload().getOrDefault("traceId", "")));
    }

    @SuppressWarnings("unchecked")
    private DashboardRequest dashboardRequestFrom(final ToolRequest request) {
        Map<String, Object> p = request.payload();
        int duration = 0;
        try {
            Object d = p.get("duration");
            if (d != null) {
                duration = Integer.parseInt(String.valueOf(d));
            }
        } catch (Exception e) { }
        
        Map<String, String> vars = new java.util.HashMap<>();
        Object vObj = p.get("variables");
        if (vObj instanceof Map<?, ?> vMap) {
            vMap.forEach((k, v) -> vars.put(String.valueOf(k), String.valueOf(v)));
        }
        
        return new DashboardRequest(
            String.valueOf(p.getOrDefault("guid", "")),
            String.valueOf(p.getOrDefault("name", "")),
            String.valueOf(p.getOrDefault("compareWith", "")),
            String.valueOf(p.getOrDefault("pageGuid", "")),
            duration,
            vars
        );
    }

    private ToolResponse runQuery(final NrqlQueryRequest qr, final ToolContext ctx) {
        String n = ctx.env("NRQL_OVERRIDE");
        if (n == null || n.isBlank() || n.equals("null")) n = queryBuilder.build(qr);
        return client.execute(n, ctx);
    }

    @SuppressWarnings("unchecked")
    private NrqlQueryRequest requestFrom(final ToolRequest request) {
        Map<String, Object> p = request.payload();
        return new NrqlQueryRequest(
            String.valueOf(p.getOrDefault("metric", "throughput")),
            (Map<String, String>) p.getOrDefault("filters", Map.of()),
            Integer.parseInt(String.valueOf(p.getOrDefault("duration", DEFAULT_TIME_WINDOW))),
            Integer.parseInt(String.valueOf(p.getOrDefault("limit", DEFAULT_LIMIT))),
            String.valueOf(p.getOrDefault("groupBy", ""))
        );
    }
}
