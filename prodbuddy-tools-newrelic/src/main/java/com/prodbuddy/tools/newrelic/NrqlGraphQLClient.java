package com.prodbuddy.tools.newrelic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolResponse;

public final class NrqlGraphQLClient {

    private final HttpClient client;

    public NrqlGraphQLClient() {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public ToolResponse execute(String nrql, ToolContext context) {
        String accountId = context.env("NEWRELIC_ACCOUNT_ID");
        if (accountId == null || accountId.isBlank()) {
            return ToolResponse.failure("NEWRELIC_CONFIG", "NEWRELIC_ACCOUNT_ID is required for NRQL queries");
        }
        String query = "{\"query\":\"{ actor { account(id: " + accountId + ") { nrql(query: \\\""
                + escape(nrql) + "\\\") { results } } } }\"}";
        return query(query, context);
    }

    public ToolResponse query(String graphqlBody, ToolContext context) {
        String apiKey = context.env("NEWRELIC_USER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return ToolResponse.failure("NEWRELIC_CONFIG", "NEWRELIC_USER_API_KEY is required");
        }

        String url = context.envOrDefault("NEWRELIC_GRAPHQL_URL", "https://api.newrelic.com/graphql");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(graphqlBody))
                .build();
        return send(request, graphqlBody);
    }

    private String escape(String text) {
        return text.replace("\"", "\\\\\"");
    }

    private ToolResponse send(HttpRequest request, String query) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return ToolResponse.ok(Map.of("status", response.statusCode(), "body", response.body(), "query", query));
        } catch (Exception exception) {
            return ToolResponse.failure("NEWRELIC_QUERY_FAILED", exception.getMessage());
        }
    }
}
