package com.prodbuddy.tools.elasticsearch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

public final class ElasticsearchTool implements Tool {

    private static final String NAME = "elasticsearch";
    private final ElasticsearchQueryBuilder queryBuilder;
    private final ElasticReadOnlyGuard readOnlyGuard;
    private final HttpClient client;

    public ElasticsearchTool(ElasticsearchQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
        this.readOnlyGuard = new ElasticReadOnlyGuard();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                NAME,
                "Elasticsearch analyzer/query tool",
                Set.of("elastic.analyze", "elastic.query", "elastic.search", "elastic.count", "elastic.request")
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        return "elasticsearch".equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        String operation = request.operation().toLowerCase();
        if ("analyze".equals(operation)) {
            String query = queryBuilder.fromPayload(request.payload());
            return ToolResponse.ok(Map.of("query", query, "analysis", "Query validated for v1 guardrails."));
        }

        if ("query".equals(operation) || "search".equals(operation)) {
            return executeRequest(request, context, "_search", "POST");
        }
        if ("count".equals(operation)) {
            return executeRequest(request, context, "_count", "POST");
        }
        if ("request".equals(operation)) {
            return executeRawRequest(request, context);
        }

        return ToolResponse.failure("ELASTIC_UNSUPPORTED_OPERATION", "supported operations are analyze, query, search, count, request");
    }

    private ToolResponse executeRawRequest(ToolRequest request, ToolContext context) {
        String endpoint = String.valueOf(request.payload().getOrDefault("endpoint", "_search"));
        String method = String.valueOf(request.payload().getOrDefault("method", "POST")).toUpperCase();
        return executeRequest(request, context, endpoint, method);
    }

    private ToolResponse executeRequest(ToolRequest request, ToolContext context, String endpoint, String method) {
        String baseUrl = context.env("ELASTICSEARCH_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            return ToolResponse.failure("ELASTIC_CONFIG", "ELASTICSEARCH_BASE_URL is required");
        }
        if (!readOnlyGuard.isAllowed(endpoint, method)) {
            return ToolResponse.failure("ELASTIC_READ_ONLY", "endpoint/method not allowed in read-only mode");
        }

        String index = resolveIndex(request, context);
        String body = queryBuilder.fromPayload(request.payload());
        int timeoutSeconds = Integer.parseInt(context.envOrDefault("ELASTICSEARCH_TIMEOUT_SECONDS", "15"));
        int maxBodyChars = Integer.parseInt(context.envOrDefault("ELASTICSEARCH_MAX_BODY_CHARS", "20000"));
        HttpRequest.Builder builder = createRequest(baseUrl, index, endpoint, method, body, timeoutSeconds);
        addAuthHeader(builder, request, context);

        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return ToolResponse.ok(Map.of(
                    "status", response.statusCode(),
                    "body", truncate(response.body(), maxBodyChars),
                    "endpoint", endpoint,
                    "method", method,
                    "truncated", response.body() != null && response.body().length() > maxBodyChars
            ));
        } catch (Exception ex) {
            return elasticFailure(ex);
        }
    }

    private ToolResponse elasticFailure(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return ToolResponse.failure("ELASTIC_QUERY_FAILED", message);
    }

    private String resolveIndex(ToolRequest request, ToolContext context) {
        return String.valueOf(
                request.payload().getOrDefault("index", context.envOrDefault("ELASTICSEARCH_DEFAULT_INDEX", "_all"))
        );
    }

    private HttpRequest.Builder createRequest(
            String baseUrl,
            String index,
            String endpoint,
            String method,
            String body,
            int timeoutSeconds
    ) {
        String path = normalizePath(baseUrl, index, endpoint);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(path))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json");
        if ("GET".equalsIgnoreCase(method)) {
            return builder.GET();
        }
        return builder.method(method, HttpRequest.BodyPublishers.ofString(body));
    }

    private String normalizePath(String baseUrl, String index, String endpoint) {
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String cleanEndpoint = endpoint.startsWith("/") ? endpoint.substring(1) : endpoint;
        return cleanBase + "/" + index + "/" + cleanEndpoint;
    }

    private String truncate(String body, int maxBodyChars) {
        if (body == null || body.length() <= maxBodyChars) {
            return body;
        }
        return body.substring(0, maxBodyChars);
    }

    private void addAuthHeader(
            HttpRequest.Builder builder,
            ToolRequest request,
            ToolContext context
    ) {
        String apiKey = context.env("ELASTICSEARCH_API_KEY");
        boolean authEnabled = Boolean.parseBoolean(
                String.valueOf(request.payload().getOrDefault(
                        "authEnabled",
                        context.envOrDefault("ELASTICSEARCH_AUTH_ENABLED", "true")
                ))
        );
        if (authEnabled && apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "ApiKey " + apiKey);
        }
    }
}
