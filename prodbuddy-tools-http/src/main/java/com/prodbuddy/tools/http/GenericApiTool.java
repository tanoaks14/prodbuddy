package com.prodbuddy.tools.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.List;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/** Generic API tool implementation. */
public final class GenericApiTool implements Tool {

    /** Tool name. */
    private static final String NAME = "http";
    /** Default timeout in seconds. */
    private static final int DEFAULT_TIMEOUT = 5;

    /** Method support guard. */
    private final HttpMethodSupport methodSupport;
    /** HTTP client. */
    private final HttpClient client;
    /** Sequence logger. */
    private final SequenceLogger seqLog;

    /**
     * Constructor.
     * @param support Method support guard.
     */
    public GenericApiTool(final HttpMethodSupport support) {
        this(support, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT))
                .build());
    }

    /**
     * Protected constructor for testing.
     * @param support Method support guard.
     * @param httpClient HTTP client.
     */
    protected GenericApiTool(final HttpMethodSupport support,
                             final HttpClient httpClient) {
        this.methodSupport = support;
        this.client = httpClient;
        this.seqLog = new Slf4jSequenceLogger(GenericApiTool.class);
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                NAME,
                "Generic API tool with optional auth",
                Set.of("http.get", "http.post", "http.put", "http.patch",
                        "http.delete", "http.head")
        );
    }

    @Override
    public boolean supports(final ToolRequest request) {
        return "http".equalsIgnoreCase(request.intent())
                || "api".equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(final ToolRequest request,
                                final ToolContext context) {
        seqLog.logSequence("AgentLoopOrchestrator", "http", "execute",
                "HTTP " + request.operation());
        String method = String.valueOf(request.payload().getOrDefault("method",
                request.operation())).toUpperCase();
        if (!methodSupport.supports(method)) {
            return ToolResponse.failure("HTTP_METHOD",
                    "Unsupported method: " + method);
        }

        String url = String.valueOf(request.payload().getOrDefault("url", ""));
        if (url.isBlank()) {
            return ToolResponse.failure("HTTP_URL", "url is required");
        }

        seqLog.logSequence("http", "ExternalAPI", "send", method + " " + url);
        HttpRequest httpRequest = buildRequest(method, url, request.payload(),
                context);
        return send(httpRequest, method, url, request, context);
    }

    private HttpRequest buildRequest(
            final String method,
            final String url,
            final Map<String, Object> payload,
            final ToolContext context
    ) {
        int timeoutSeconds = Integer.parseInt(context.envOrDefault(
                "HTTP_DEFAULT_TIMEOUT_SECONDS", "20"));
        String body = String.valueOf(payload.getOrDefault("body", ""));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds));
        addHeaders(builder, payload, context);
        return builder.method(method, bodyPublisher(method, body)).build();
    }

    private void addHeaders(
            final HttpRequest.Builder builder,
            final Map<String, Object> payload,
            final ToolContext context
    ) {
        builder.header("Content-Type", String.valueOf(payload.getOrDefault(
                "contentType", "application/json")));
        boolean authEnabled = Boolean.parseBoolean(
                String.valueOf(payload.getOrDefault(
                        "authEnabled",
                        context.envOrDefault("HTTP_DEFAULT_AUTH_ENABLED",
                                "false")
                ))
        );
        if (!authEnabled) {
            return;
        }

        String token = String.valueOf(payload.getOrDefault(
                "bearerToken",
                context.envOrDefault("HTTP_BEARER_TOKEN", "")
        ));
        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    private HttpRequest.BodyPublisher bodyPublisher(final String method,
                                                    final String body) {
        if ("GET".equals(method) || "DELETE".equals(method)
                || "HEAD".equals(method)) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofString(body);
    }

    private ToolResponse send(final HttpRequest httpRequest,
                              final String method,
                              final String url,
                              final ToolRequest toolRequest,
                              final ToolContext context) {
        try {
            HttpResponse<String> response = client.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());
            seqLog.logSequence("ExternalAPI", "http", "send",
                    "Response: " + response.statusCode());

            int maxChars = resolveMaxChars(toolRequest, context);
            String body = response.body();
            String finalBody = (body != null && body.length() > maxChars)
                    ? body.substring(0, maxChars) : body;

            Map<String, Object> data = new HashMap<>();
            data.put("method", method);
            data.put("url", url);
            data.put("status", response.statusCode());
            data.put("body", finalBody);
            data.put("truncated", body != null && body.length() > maxChars);

            tryParseJson(response.body(), data);
            return ToolResponse.ok(data);
        } catch (Exception exception) {
            seqLog.logSequence("ExternalAPI", "http", "send",
                    "Failed: " + exception.getMessage());
            return ToolResponse.failure("HTTP_CALL_FAILED",
                    exception.getMessage());
        }
    }

    int resolveMaxChars(final ToolRequest req, final ToolContext ctx) {
        boolean noTrunc = Boolean.parseBoolean(String.valueOf(
                req.payload().getOrDefault("noTruncate", "false")));
        if (noTrunc) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(String.valueOf(req.payload().getOrDefault(
                "maxOutputChars", ctx.envOrDefault(
                        "HTTP_MAX_OUTPUT_CHARS", "20000"))));
    }

    void tryParseJson(final String body,
                      final Map<String, Object> data) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(body);
            if (node.isObject()) {
                data.put("jsonBody", mapper.convertValue(node, Map.class));
            } else if (node.isArray()) {
                data.put("jsonBody", mapper.convertValue(node, List.class));
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
    }
}
