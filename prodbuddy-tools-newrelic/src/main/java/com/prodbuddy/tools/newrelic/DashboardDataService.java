package com.prodbuddy.tools.newrelic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolResponse;

public final class DashboardDataService {
    private final NrqlGraphQLClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public DashboardDataService(NrqlGraphQLClient client) {
        this.client = client;
    }

    public ToolResponse getDashboardData(DashboardRequest req, ToolResponse dash, ToolContext ctx) {
        try {
            JsonNode root = mapper.readTree(String.valueOf(dash.data().get("body")));
            JsonNode pages = root.path("data").path("actor").path("entity").path("pages");
            JsonNode selectedPage = pages.path(0);
            if (!req.pageGuid().isEmpty()) {
                for (JsonNode p : pages) {
                    if (req.pageGuid().equals(p.path("guid").asText())) { selectedPage = p; break; }
                }
            }
            return ToolResponse.ok(Map.of("dashboard", req.guid(), "results", processWidgets(selectedPage.path("widgets"), req, ctx)));
        } catch (Exception e) { return ToolResponse.failure("DASHBOARD_DATA_ERROR", e.getMessage()); }
    }

    private List<Map<String, Object>> processWidgets(JsonNode ws, DashboardRequest req, ToolContext ctx) throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < Math.min(ws.size(), 10); i++) {
            JsonNode w = ws.get(i);
            String n = extractQuery(w);
            if (n != null && !n.isEmpty()) {
                if (!req.compareWith().isEmpty() && !n.toLowerCase().contains("compare with")) n += " COMPARE WITH " + req.compareWith();
                if (req.duration() > 0 && !n.toLowerCase().contains("since ")) n += " SINCE " + req.duration() + " minutes ago";
                
                ToolResponse res = client.execute(n, ctx);
                Object data;
                if (res.success()) {
                    JsonNode body = mapper.readTree(String.valueOf(res.data().get("body")));
                    if (body.has("errors")) {
                        data = "GraphQL Error: " + body.get("errors").toString();
                    } else {
                        data = body.path("data").path("actor").path("account").path("nrql").path("results");
                    }
                } else {
                    data = "Error: " + res.errors();
                }
                results.add(Map.of("title", w.path("title").asText(), "query", n, "data", data));
            }
        }
        return results;
    }

    private String extractQuery(JsonNode w) throws Exception {
        // Try configuration (typed)
        JsonNode config = w.path("configuration").path("nrqlQueries");
        if (config.isArray() && config.size() > 0) {
            return config.get(0).path("query").asText();
        }
        // Try rawConfiguration (untyped)
        JsonNode rcNode = w.path("rawConfiguration");
        if (!rcNode.isMissingNode() && !rcNode.isNull()) {
            JsonNode rc = rcNode.isObject() ? rcNode : mapper.readTree(rcNode.asText());
            JsonNode rqs = rc.path("nrqlQueries");
            if (rqs.isArray() && rqs.size() > 0) {
                return rqs.get(0).path("query").asText();
            }
        }
        return null;
    }
}
