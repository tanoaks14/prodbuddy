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
            JsonNode rcNode = w.path("rawConfiguration");
            if (rcNode.isMissingNode()) continue;
            JsonNode rc = rcNode.isObject() ? rcNode : mapper.readTree(rcNode.asText());
            String n = rc.path("nrqlQueries").path(0).path("query").asText();
            if (!n.isEmpty()) {
                if (!req.compareWith().isEmpty() && !n.toLowerCase().contains("compare with")) n += " COMPARE WITH " + req.compareWith();
                ToolResponse res = client.execute(n, ctx);
                Object data = res.success() ? mapper.readTree(String.valueOf(res.data().get("body"))).path("data").path("actor").path("account").path("nrql").path("results") : "Error executing query";
                results.add(Map.of("title", w.path("title").asText(), "query", n, "data", data));
            }
        }
        return results;
    }
}
