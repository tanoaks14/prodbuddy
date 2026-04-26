package com.prodbuddy.tools.elasticsearch;

import com.prodbuddy.core.tool.*;
import com.prodbuddy.observation.SequenceLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Elasticsearch read-only query and analysis tool. */
public final class ElasticsearchTool implements Tool {

    private static final String NAME = "elasticsearch";
    private final ElasticsearchQueryBuilder queryBuilder;
    private final ElasticReadOnlyGuard readOnlyGuard;
    private final HttpClient client;
    private final SequenceLogger seqLog;

    /** Primary constructor. */
    public ElasticsearchTool(final ElasticsearchQueryBuilder b) {
        this.queryBuilder = b;
        this.readOnlyGuard = new ElasticReadOnlyGuard();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();
        this.seqLog = com.prodbuddy.observation.ObservationContext.getLogger();
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(NAME, "Elasticsearch tool",
                Set.of("elasticsearch.analyze", "elasticsearch.query",
                        "elasticsearch.search", "elasticsearch.count",
                        "elasticsearch.request"));
    }

    @Override
    public ToolStyling styling() {
        return new ToolStyling("#B3E5FC", "#01579B", "#E1F5FE", "🔎 Elasticsearch", java.util.Map.of());
    }

    @Override
    public boolean supports(final ToolRequest request) {
        return "elasticsearch".equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(
            final ToolRequest request, final ToolContext context) {
        seqLog.logSequence("AgentLoopOrchestrator",
                "Elasticsearch", "execute",
                "ES " + request.operation(), styling().toMetadata("Elasticsearch"));
        final String op = request.operation().toLowerCase();
        switch (op) {
            case "analyze": return handleAnalyze(request);
            case "query":
            case "search":
                return executeRequest(request, context,
                        "_search", "POST");
            case "count":
                return executeRequest(request, context,
                        "_count", "POST");
            case "request":
                return executeRawRequest(request, context);
            default:
                return ToolResponse.failure(
                        "ELASTIC_UNSUPPORTED_OPERATION",
                        "supported: analyze,query,search,count,request");
        }
    }

    private ToolResponse handleAnalyze(final ToolRequest request) {
        final String query = queryBuilder.fromPayload(
                request.payload());
        seqLog.logSequence("Elasticsearch",
                "AgentLoopOrchestrator", "execute", "Analyzed", styling().toMetadata("Elasticsearch"));
        return ToolResponse.ok(Map.of("query", query,
                "analysis", "Query validated for v1 guardrails."));
    }

    private ToolResponse executeRawRequest(
            final ToolRequest request, final ToolContext context) {
        final String ep = String.valueOf(
                request.payload().getOrDefault("endpoint", "_search"));
        final String m = String.valueOf(
                request.payload().getOrDefault("method", "POST"))
                .toUpperCase();
        return executeRequest(request, context, ep, m);
    }

    private ToolResponse executeRequest(
            final ToolRequest request, final ToolContext context,
            final String endpoint, final String method) {
        final String baseUrl = context.env("ELASTICSEARCH_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            return ToolResponse.failure("ELASTIC_CONFIG",
                    "ELASTICSEARCH_BASE_URL is required");
        }
        if (!readOnlyGuard.isAllowed(endpoint, method)) {
            return ToolResponse.failure("ELASTIC_READ_ONLY",
                    "endpoint/method not allowed");
        }
        return sendRequest(request, context, baseUrl, endpoint, method);
    }

    private ToolResponse sendRequest(
            final ToolRequest request, final ToolContext context,
            final String baseUrl, final String endpoint,
            final String method) {
        final String index = resolveIndex(request, context);
        final String body = queryBuilder.fromPayload(request.payload());
        final int timeout = Integer.parseInt(context.envOrDefault(
                "ELASTICSEARCH_TIMEOUT_SECONDS", "15"));
        final boolean noTruncate = Boolean.parseBoolean(String.valueOf(request.payload().getOrDefault("noTruncate", "false")));
        final int maxChars = noTruncate ? Integer.MAX_VALUE : Integer.parseInt(String.valueOf(request.payload().getOrDefault("maxOutputChars", 
                context.envOrDefault("ELASTICSEARCH_MAX_BODY_CHARS", "20000"))));
        final HttpRequest.Builder builder = createRequest(
                baseUrl, index, endpoint, method, body, timeout);
        addAuthHeader(builder, request, context);
        return doSend(builder, endpoint, method, maxChars, index, body);
    }

    private ToolResponse doSend(
            final HttpRequest.Builder builder, final String endpoint,
            final String method, final int maxChars,
            final String index, final String body) {
        try {
            Map<String, String> qMeta = new java.util.HashMap<>(styling().toMetadata("Elasticsearch"));
            qMeta.put("style", "query");
            qMeta.put("noteText", "Index: " + index + "\nQuery: " + body);
            seqLog.logSequence("Elasticsearch", "ElasticCluster",
                    "executeRequest", method + " " + endpoint, qMeta);
            final HttpResponse<String> resp = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            Map<String, String> rMeta = new java.util.HashMap<>(styling().toMetadata("Elasticsearch"));
            rMeta.put("style", status >= 400 ? "error" : "success");
            rMeta.put("noteText", "HTTP " + status + "\n\n" + truncate(resp.body(), 2000));
            seqLog.logSequence("ElasticCluster", "Elasticsearch",
                    "executeRequest", "HTTP " + status, rMeta);
            final boolean trunc = resp.body() != null
                    && resp.body().length() > maxChars;
            return ToolResponse.ok(Map.of("status", resp.statusCode(),
                    "body", truncate(resp.body(), maxChars),
                    "endpoint", endpoint, "method", method,
                    "truncated", trunc));
        } catch (Exception ex) {
            seqLog.logSequence("ElasticCluster", "Elasticsearch",
                    "executeRequest", "Failed");
            return elasticFailure(ex);
        }
    }

    private ToolResponse elasticFailure(final Exception exception) {
        String msg = exception.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = exception.getClass().getSimpleName();
        }
        return ToolResponse.failure("ELASTIC_QUERY_FAILED", msg);
    }

    private String resolveIndex(
            final ToolRequest request, final ToolContext context) {
        return String.valueOf(request.payload().getOrDefault("index",
                context.envOrDefault(
                        "ELASTICSEARCH_DEFAULT_INDEX", "_all")));
    }

    private HttpRequest.Builder createRequest(
            final String baseUrl, final String index,
            final String endpoint, final String method,
            final String body, final int timeoutSeconds) {
        final String path = normalizePath(baseUrl, index, endpoint);
        final HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(path))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json");
        if ("GET".equalsIgnoreCase(method)) {
            return b.GET();
        }
        return b.method(method,
                HttpRequest.BodyPublishers.ofString(body));
    }

    private String normalizePath(
            final String baseUrl, final String index,
            final String endpoint) {
        final String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        final String ep = endpoint.startsWith("/")
                ? endpoint.substring(1) : endpoint;
        return base + "/" + index + "/" + ep;
    }

    private String truncate(final String body, final int max) {
        if (body == null || body.length() <= max) {
            return body;
        }
        return body.substring(0, max);
    }

    private void addAuthHeader(
            final HttpRequest.Builder builder,
            final ToolRequest request, final ToolContext context) {
        final String apiKey = context.env("ELASTICSEARCH_API_KEY");
        final boolean auth = Boolean.parseBoolean(String.valueOf(
                request.payload().getOrDefault("authEnabled",
                        context.envOrDefault(
                                "ELASTICSEARCH_AUTH_ENABLED", "true"))));
        if (auth && apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "ApiKey " + apiKey);
        }
    }
}
