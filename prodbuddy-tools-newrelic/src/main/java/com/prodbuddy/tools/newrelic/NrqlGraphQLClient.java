package com.prodbuddy.tools.newrelic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;

/**
 * Client for interacting with New Relic NerdGraph API.
 */
public final class NrqlGraphQLClient {

    private static final int CONNECT_TIMEOUT_SEC = 5;
    private static final int REQUEST_TIMEOUT_SEC = 20;

    /** HTTP client. */
    private final HttpClient client;
    /** Logger. */
    private final SequenceLogger seqLog;
    /** Styling. */
    private final com.prodbuddy.core.tool.ToolStyling styling;

    /**
     * Create a client.
     * @param seqLog the logger
     * @param styling the styling
     */
    public NrqlGraphQLClient(final SequenceLogger seqLog,
                             final com.prodbuddy.core.tool.ToolStyling styling) {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
            .build();
        this.seqLog = seqLog;
        this.styling = styling;
    }

    /**
     * Execute a NRQL query via NerdGraph.
     * @param nrql the query string
     * @param context the tool context
     * @return the response
     */
    public ToolResponse execute(final String nrql, final ToolContext context) {
        String accountId = context.env("NEWRELIC_ACCOUNT_ID");
        if (accountId == null || accountId.isBlank()) {
            return ToolResponse.failure("NEWRELIC_CONFIG",
                "NEWRELIC_ACCOUNT_ID is required for NRQL queries");
        }
        // Properly escape the NRQL query for inclusion in a JSON-encoded GraphQL query
        String escapedNrql = escape(nrql);
        String query = "{\"query\":\"{ actor { account(id: " + accountId
            + ") { nrql(query: \\\"" + escapedNrql + "\\\") { results } } } }\"}";
        return query(query, context);
    }

    /**
     * Execute a raw GraphQL query.
     * @param graphqlBody the full JSON request body
     * @param context the tool context
     * @return the response
     */
    public ToolResponse query(final String graphqlBody, final ToolContext context) {
        String apiKey = context.env("NEWRELIC_USER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return ToolResponse.failure("NEWRELIC_CONFIG",
                "NEWRELIC_USER_API_KEY is required");
        }

        boolean debug = "true".equalsIgnoreCase(context.envOrDefault("DEBUG", "false"));
        if (debug) {
            seqLog.logSequence("newrelic", "NerdGraph", "query", "Executing GraphQL Query", 
                Map.of("type", "note", "noteText", "GQL: " + graphqlBody));
        }

        String region = context.envOrDefault("NEWRELIC_REGION", "US")
            .toUpperCase();
        String defaultUrl = region.equals("EU")
            ? "https://api.eu.newrelic.com/graphql"
            : "https://api.newrelic.com/graphql";
        String url = context.envOrDefault("NEWRELIC_GRAPHQL_URL", defaultUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                .header("Content-Type", "application/json")
                .header("API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(graphqlBody))
                .build();
        return send(request, graphqlBody, debug);
    }

    private String escape(final String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\\\"")
                   .replace("\n", " ")
                   .replace("\r", "")
                   .replace("\t", " ");
    }

    private ToolResponse send(final HttpRequest request,
                              final String query,
                              final boolean debug) {
        try {
            Map<String, String> qMeta = new java.util.HashMap<>(styling.toMetadata());
            qMeta.put("style", "query");
            qMeta.put("noteText", "NRQL/GQL Query:\n" + query);
            
            seqLog.logSequence("newrelic", "NerdGraph", "POST", "Executing GraphQL", qMeta);
            
            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
            
            int status = response.statusCode();
            Map<String, String> rMeta = new java.util.HashMap<>(styling.toMetadata());
            rMeta.put("style", status >= 400 ? "error" : "success");
            
            seqLog.logSequence("NerdGraph", "newrelic", "RESPONSE", "HTTP " + status, rMeta);

            if (debug) {
                seqLog.logSequence("newrelic", "Agent", "debug", 
                        "Body: " + response.body());
            }

            return ToolResponse.ok(Map.of("status", response.statusCode(),
                "body", response.body(), "query", query));
        } catch (Exception exception) {
            return ToolResponse.failure("NEWRELIC_QUERY_FAILED",
                exception.getMessage());
        }
    }
}
