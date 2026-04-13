package com.prodbuddy.tools.splunk;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

public final class SplunkTool implements Tool {

    private static final String NAME = "splunk";
    private static final String MODE_TOKEN = "token";
    private static final String MODE_USER = "user";
    private static final Pattern SESSION_KEY_JSON = Pattern.compile("\"sessionKey\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SESSION_KEY_XML = Pattern.compile("<sessionKey>([^<]+)</sessionKey>");

    private final SplunkOperationGuard guard;
    private final SplunkQueryBuilder queryBuilder;
    private final HttpClient client;

    public SplunkTool(SplunkOperationGuard guard) {
        this.guard = guard;
        this.queryBuilder = new SplunkQueryBuilder();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                NAME,
                "Splunk read-only search tool",
                Set.of("splunk.search", "splunk.oneshot", "splunk.jobs", "splunk.results")
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        return "splunk".equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        String operation = request.operation().toLowerCase();
        if (!guard.isAllowed(operation)) {
            return ToolResponse.failure("SPLUNK_FORBIDDEN", "Only read/search operations are allowed");
        }

        String baseUrl = context.env("SPLUNK_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            return ToolResponse.failure("SPLUNK_CONFIG", "SPLUNK_BASE_URL is required");
        }

        boolean authEnabled = resolveAuthEnabled(request, context);
        String authHeader = resolveAuthHeader(request, context, baseUrl, authEnabled);
        if (authEnabled && authHeader == null) {
            return ToolResponse.failure("SPLUNK_CONFIG", credentialsMessage(request, context));
        }

        String search = queryBuilder.resolveSearch(request, context);
        HttpRequest httpRequest = buildRequest(operation, baseUrl, request.payload(), search, authHeader);
        return send(httpRequest, operation, search);
    }

    private ToolResponse send(HttpRequest request, String operation, String search) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return ToolResponse.ok(Map.of(
                    "status", response.statusCode(),
                    "body", response.body(),
                    "operation", operation,
                    "search", search
            ));
        } catch (IOException ex) {
            return ToolResponse.failure("SPLUNK_QUERY_FAILED", messageFrom(ex));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ToolResponse.failure("SPLUNK_QUERY_FAILED", messageFrom(ex));
        }
    }

    private String messageFrom(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }

    private String credentialsMessage(ToolRequest request, ToolContext context) {
        String mode = resolveAuthMode(request, context);
        if (MODE_USER.equals(mode)) {
            return "SPLUNK_USERNAME and SPLUNK_PASSWORD are required for authMode=user";
        }
        return "SPLUNK_TOKEN is required for authMode=token";
    }

    private String resolveAuthHeader(
            ToolRequest request,
            ToolContext context,
            String baseUrl,
            boolean authEnabled
    ) {
        if (!authEnabled) {
            return null;
        }

        String mode = resolveAuthMode(request, context);
        if (MODE_USER.equals(mode)) {
            return loginAndBuildAuthHeader(request, context, baseUrl);
        }

        String token = resolveToken(request, context);
        if (token == null || token.isBlank()) {
            return null;
        }
        return "Splunk " + token;
    }

    private String resolveAuthMode(ToolRequest request, ToolContext context) {
        return String.valueOf(
                request.payload().getOrDefault("authMode", context.envOrDefault("SPLUNK_AUTH_MODE", MODE_TOKEN))
        ).toLowerCase();
    }

    private String resolveToken(ToolRequest request, ToolContext context) {
        Object payloadToken = request.payload().get("token");
        if (payloadToken != null && !String.valueOf(payloadToken).isBlank()) {
            return String.valueOf(payloadToken);
        }
        return context.env("SPLUNK_TOKEN");
    }

    private String loginAndBuildAuthHeader(ToolRequest request, ToolContext context, String baseUrl) {
        String username = resolveCredential("username", "SPLUNK_USERNAME", request, context);
        String password = resolveCredential("password", "SPLUNK_PASSWORD", request, context);
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return null;
        }

        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/services/auth/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(buildLoginBody(username, password)))
                .build();

        try {
            HttpResponse<String> response = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
            String sessionKey = extractSessionKey(response.body());
            if (sessionKey == null || sessionKey.isBlank()) {
                return null;
            }
            return "Splunk " + sessionKey;
        } catch (IOException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String resolveCredential(String payloadKey, String envKey, ToolRequest request, ToolContext context) {
        Object payloadValue = request.payload().get(payloadKey);
        if (payloadValue != null && !String.valueOf(payloadValue).isBlank()) {
            return String.valueOf(payloadValue);
        }
        return context.env(envKey);
    }

    private String buildLoginBody(String username, String password) {
        return "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                + "&output_mode=json";
    }

    private String extractSessionKey(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        Matcher json = SESSION_KEY_JSON.matcher(body);
        if (json.find()) {
            return json.group(1);
        }

        Matcher xml = SESSION_KEY_XML.matcher(body);
        if (xml.find()) {
            return xml.group(1);
        }

        return null;
    }

    private boolean resolveAuthEnabled(ToolRequest request, ToolContext context) {
        return Boolean.parseBoolean(
                String.valueOf(request.payload().getOrDefault(
                        "authEnabled",
                        context.envOrDefault("SPLUNK_AUTH_ENABLED", "true")
                ))
        );
    }

    private HttpRequest buildRequest(
            String operation,
            String baseUrl,
            Map<String, Object> payload,
            String search,
            String authHeader
    ) {
        String path = queryBuilder.resolvePath(operation, payload);
        String body = queryBuilder.buildBody(operation, payload, search);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (authHeader != null && !authHeader.isBlank()) {
            builder.header("Authorization", authHeader);
        }

        return builder.build();
    }
}
