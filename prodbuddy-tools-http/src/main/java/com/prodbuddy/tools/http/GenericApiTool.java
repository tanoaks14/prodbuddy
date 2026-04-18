package com.prodbuddy.tools.http;

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
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

public final class GenericApiTool implements Tool {

    private static final String NAME = "http";
    private final HttpMethodSupport methodSupport;
    private final HttpClient client;
    private final SequenceLogger seqLog;

    public GenericApiTool(HttpMethodSupport methodSupport) {
        this.methodSupport = methodSupport;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.seqLog = new Slf4jSequenceLogger(GenericApiTool.class);
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                NAME,
                "Generic API tool with optional auth",
                Set.of("http.get", "http.post", "http.put", "http.patch", "http.delete", "http.head")
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        return "http".equalsIgnoreCase(request.intent()) || "api".equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        seqLog.logSequence("AgentLoopOrchestrator", "http", "execute", "HTTP " + request.operation());
        String method = String.valueOf(request.payload().getOrDefault("method", request.operation())).toUpperCase();
        if (!methodSupport.supports(method)) {
            return ToolResponse.failure("HTTP_METHOD", "Unsupported method: " + method);
        }

        String url = String.valueOf(request.payload().getOrDefault("url", ""));
        if (url.isBlank()) {
            return ToolResponse.failure("HTTP_URL", "url is required");
        }

        seqLog.logSequence("http", "ExternalAPI", "send", method + " " + url);
        HttpRequest httpRequest = buildRequest(method, url, request.payload(), context);
        return send(httpRequest, method, url);
    }

    private HttpRequest buildRequest(
            String method,
            String url,
            Map<String, Object> payload,
            ToolContext context
    ) {
        int timeoutSeconds = Integer.parseInt(context.envOrDefault("HTTP_DEFAULT_TIMEOUT_SECONDS", "20"));
        String body = String.valueOf(payload.getOrDefault("body", ""));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds));
        addHeaders(builder, payload, context);
        return builder.method(method, bodyPublisher(method, body)).build();
    }

    private void addHeaders(
            HttpRequest.Builder builder,
            Map<String, Object> payload,
            ToolContext context
    ) {
        builder.header("Content-Type", String.valueOf(payload.getOrDefault("contentType", "application/json")));
        boolean authEnabled = Boolean.parseBoolean(
                String.valueOf(payload.getOrDefault(
                        "authEnabled",
                        context.envOrDefault("HTTP_DEFAULT_AUTH_ENABLED", "false")
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

    private HttpRequest.BodyPublisher bodyPublisher(String method, String body) {
        if ("GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method)) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofString(body);
    }

    private ToolResponse send(HttpRequest httpRequest, String method, String url) {
        try {
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            seqLog.logSequence("ExternalAPI", "http", "send", "Response: " + response.statusCode());
            Map<String, Object> responseData = new java.util.HashMap<>();
            responseData.put("method", method);
            responseData.put("url", url);
            responseData.put("status", response.statusCode());
            responseData.put("body", response.body());
            
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(response.body());
                if (node.isObject()) {
                    responseData.put("jsonBody", mapper.convertValue(node, Map.class));
                } else if (node.isArray()) {
                    responseData.put("jsonBody", mapper.convertValue(node, java.util.List.class));
                }
            } catch (Exception e) {
                // Ignore parsing errors, it just means body is not json
            }

            return ToolResponse.ok(responseData);
        } catch (Exception exception) {
            seqLog.logSequence("ExternalAPI", "http", "send", "Failed: " + exception.getMessage());
            return ToolResponse.failure("HTTP_CALL_FAILED", exception.getMessage());
        }
    }
}
