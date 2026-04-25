package com.prodbuddy.tools.newrelic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;

public final class DashboardDataService {
    private final NrqlGraphQLClient client;
    private final SequenceLogger seqLog;
    private final ObjectMapper mapper = new ObjectMapper();

    public DashboardDataService(final NrqlGraphQLClient client,
                                final SequenceLogger seqLog) {
        this.client = client;
        this.seqLog = seqLog;
    }

    public ToolResponse getDashboardData(final DashboardRequest req,
                                         final ToolResponse dash,
                                         final ToolContext ctx) {
        seqLog.logSequence("newrelic", "DashboardDataService", "getDashboardData",
                "Extracting data for dashboard: " + req.guid());
        try {
            JsonNode root = mapper.readTree(String.valueOf(dash.data().get("body")));
            JsonNode pages = root.path("data").path("actor").path("entity").path("pages");
            JsonNode selectedPage = pages.path(0);
            if (!req.pageGuid().isEmpty()) {
                for (JsonNode p : pages) {
                    if (req.pageGuid().equals(p.path("guid").asText())) {
                        selectedPage = p;
                        break;
                    }
                }
            }
            List<Map<String, Object>> results = processWidgets(
                    selectedPage.path("widgets"), req, ctx);
            seqLog.logSequence("DashboardDataService", "newrelic", "getDashboardData",
                    "Extracted data for " + results.size() + " widgets");
            return ToolResponse.ok(Map.of("dashboard", req.guid(),
                    "results", results));
        } catch (Exception e) { return ToolResponse.failure("DASHBOARD_DATA_ERROR", e.getMessage()); }
    }

    private List<Map<String, Object>> processWidgets(
            final JsonNode ws, final DashboardRequest req,
            final ToolContext ctx) throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < Math.min(ws.size(), 10); i++) {
            JsonNode w = ws.get(i);
            QueryInfo info = extractQueryInfo(w);
            if (info != null && info.query != null && !info.query.isEmpty()) {
                String n = info.query;
                seqLog.logSequence("newrelic", "DashboardDataService", "processWidgets",
                        "Raw NRQL from widget: " + n);
                // Resolve template variables before adding modifiers
                n = resolveVariables(n, req, ctx);
                seqLog.logSequence("newrelic", "DashboardDataService", "processWidgets",
                        "Resolved NRQL: " + n);
                
                if (!req.compareWith().isEmpty()
                        && !n.toLowerCase().contains("compare with")) {
                    n += " COMPARE WITH " + req.compareWith();
                }
                if (req.duration() > 0 && !n.toLowerCase().contains("since ")) {
                    n += " SINCE " + req.duration() + " minutes ago";
                }
                Object data = executeQuery(n, info.accountId, ctx);
                results.add(Map.of("title", w.path("title").asText(),
                        "query", n, "data", data != null ? data : "No data"));
            }
        }
        return results;
    }

    private Object executeQuery(final String nrql, final long accountId,
                                final ToolContext ctx) throws Exception {
        // Use accountId from widget if available, otherwise fallback to context
        ToolContext effectiveCtx = accountId > 0
                ? new ToolContext(ctx.requestId(),
                Map.of("NEWRELIC_ACCOUNT_ID", String.valueOf(accountId)),
                ctx.registry()) : ctx;

        seqLog.logSequence("DashboardDataService", "newrelic", "executeQuery",
                "Running NRQL [acc=" + (accountId > 0 ? accountId : "default")
                        + "]: " + nrql);

        ToolResponse res = client.execute(nrql, effectiveCtx);
        if (!res.success()) {
            seqLog.logSequence("newrelic", "DashboardDataService", "executeQuery",
                    "Query failed: " + res.errors());
            return "Error: " + res.errors();
        }
        JsonNode body = mapper.readTree(String.valueOf(res.data().get("body")));
        if (body.has("errors")) {
            String err = body.get("errors").toString();
            seqLog.logSequence("newrelic", "DashboardDataService", "executeQuery",
                    "GraphQL Error: " + err);
            return "GraphQL Error: " + err;
        }
        JsonNode results = body.path("data").path("actor")
                .path("account").path("nrql").path("results");
        seqLog.logSequence("newrelic", "DashboardDataService", "executeQuery",
                "Query success. Returned " + (results.isArray() ? results.size() : "some") + " rows.");
        return mapper.convertValue(results, List.class);
    }

    private String resolveVariables(final String query,
                                    final DashboardRequest req,
                                    final ToolContext ctx) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\\$\\{([^}]+)\\}|\\{\\{([^}]+)\\}\\}");
        java.util.regex.Matcher m = p.matcher(query);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        boolean hasUnresolved = false;
        while (m.find()) {
            sb.append(query, last, m.start());
            String key = m.group(1) != null ? m.group(1) : m.group(2);
            String var = key.trim();
            String val = getVariableValue(var, req, ctx);
            if (val != null) {
                sb.append(val);
            } else {
                sb.append(m.group(0));
                hasUnresolved = true;
                seqLog.logSequence("newrelic", "DashboardDataService", "resolveVariables",
                        "WARNING: Unresolved variable: " + var);
            }
            last = m.end();
        }
        sb.append(query.substring(last));
        if (hasUnresolved) {
            seqLog.logSequence("newrelic", "DashboardDataService", "resolveVariables",
                    "Query has placeholders: " + sb.toString());
        }
        return sb.toString();
    }

    private String getVariableValue(final String var,
                                    final DashboardRequest req,
                                    final ToolContext ctx) {
        // 1. Check if it's explicitly passed in the request
        if (req.variables().containsKey(var)) {
            return req.variables().get(var);
        }
        // 2. Check if it's a known dashboard param
        if ("guid".equalsIgnoreCase(var)) {
            return req.guid();
        }
        if ("pageGuid".equalsIgnoreCase(var)) {
            return req.pageGuid();
        }
        // 3. Check environment/context
        String val = ctx.env(var);
        if (val != null) {
            return val;
        }
        // 4. Fallback to empty if it looks like a required filter
        return null;
    }

    private static class QueryInfo {
        String query;
        long accountId;
    }

    private QueryInfo extractQueryInfo(JsonNode w) throws Exception {
        // Try configuration (typed)
        JsonNode config = w.path("configuration").path("nrqlQueries");
        if (config.isArray() && config.size() > 0) {
            QueryInfo info = new QueryInfo();
            info.query = config.get(0).path("query").asText();
            info.accountId = config.get(0).path("accountId").asLong();
            return info;
        }
        // Try rawConfiguration (untyped)
        JsonNode rcNode = w.path("rawConfiguration");
        if (!rcNode.isMissingNode() && !rcNode.isNull()) {
            JsonNode rc = rcNode.isObject() ? rcNode : mapper.readTree(rcNode.asText());
            JsonNode rqs = rc.path("nrqlQueries");
            if (rqs.isArray() && rqs.size() > 0) {
                QueryInfo info = new QueryInfo();
                info.query = rqs.get(0).path("query").asText();
                info.accountId = rqs.get(0).path("accountId").asLong();
                return info;
            }
        }
        return null;
    }
}
