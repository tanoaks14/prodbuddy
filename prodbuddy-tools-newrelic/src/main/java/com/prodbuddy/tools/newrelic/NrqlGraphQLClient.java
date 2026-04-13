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
        String apiKey = context.env("NEWRELIC_USER_API_KEY");
        if (invalidConfig(accountId, apiKey)) {
            return ToolResponse.failure("NEWRELIC_CONFIG", "NEWRELIC_ACCOUNT_ID and NEWRELIC_USER_API_KEY are required");
        }

        String url = context.envOrDefault("NEWRELIC_GRAPHQL_URL", "https://api.newrelic.com/graphql");
        String body = graphqlBody(accountId, nrql);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request, nrql);
    }

    private boolean invalidConfig(String accountId, String apiKey) {
        return accountId == null || apiKey == null || accountId.isBlank() || apiKey.isBlank();
    }

    private String graphqlBody(String accountId, String nrql) {
        return "{\"query\":\"{ actor { account(id: " + accountId + ") { nrql(query: \\\""
                + escape(nrql) + "\\\") { results } } } }\"}";
    }

    private String escape(String text) {
        return text.replace("\"", "\\\\\"");
    }

    private ToolResponse send(HttpRequest request, String nrql) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return ToolResponse.ok(Map.of("status", response.statusCode(), "body", response.body(), "nrql", nrql));
        } catch (Exception exception) {
            return ToolResponse.failure("NEWRELIC_QUERY_FAILED", exception.getMessage());
        }
    }
}
