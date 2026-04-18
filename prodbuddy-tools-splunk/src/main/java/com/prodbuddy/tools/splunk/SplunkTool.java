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
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

public final class SplunkTool implements Tool {

    private static final String NAME = "splunk";
    private static final String MODE_TOKEN = "token";
    private static final String MODE_USER = "user";
    private static final String MODE_SSO = "sso";
    private static final String MODE_SESSION = "session";
    private static final Pattern SESSION_KEY_JSON = Pattern.compile("\"sessionKey\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SESSION_KEY_XML = Pattern.compile("<sessionKey>([^<]+)</sessionKey>");

    private final SplunkOperationGuard guard;
    private final SplunkQueryBuilder queryBuilder;
    private final HttpClient client;
    private final SequenceLogger seqLog;

    public SplunkTool(SplunkOperationGuard guard) {
        this.guard = guard;
        this.queryBuilder = new SplunkQueryBuilder();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.seqLog = new Slf4jSequenceLogger(SplunkTool.class);
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
        seqLog.logSequence("AgentLoopOrchestrator", "splunk", "execute", "Executing Splunk " + request.operation());
        String operation = request.operation().toLowerCase();
        if (!guard.isAllowed(operation)) {
            seqLog.logSequence("splunk", "AgentLoopOrchestrator", "execute", "Forbidden operation");
            return ToolResponse.failure("SPLUNK_FORBIDDEN", "Only read/search operations are allowed");
        }

        String baseUrl = context.env("SPLUNK_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            seqLog.logSequence("splunk", "AgentLoopOrchestrator", "execute", "Missing SPLUNK_BASE_URL");
            return ToolResponse.failure("SPLUNK_CONFIG", "SPLUNK_BASE_URL is required");
        }

        boolean authEnabled = resolveAuthEnabled(request, context);
        String authMode = resolveAuthMode(request, context);
        String authHeader = resolveAuthHeader(request, context, baseUrl, authEnabled);
        if (authEnabled && authHeader == null) {
            return ToolResponse.failure("SPLUNK_CONFIG", credentialsMessage(request, context));
        }

        String search = queryBuilder.resolveSearch(request, context);
        String path = queryBuilder.resolvePath(operation, request.payload());
        String body = queryBuilder.buildBody(operation, request.payload(), search);
        seqLog.logSequence("splunk", "SplunkAPI", "send", "Sending query: " + operation);
        HttpRequest httpRequest = buildRequest(baseUrl, path, body, authHeader);
        return send(httpRequest, operation, search, path, authMode);
    }

    private ToolResponse send(HttpRequest request, String operation, String search, String path, String authMode) {
        String attempted = "operation=" + operation + ", path=" + path + ", authMode=" + authMode + ", search=" + search;
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                seqLog.logSequence("SplunkAPI", "splunk", "send", "HTTP " + response.statusCode());
                return splunkHttpFailure(response, attempted);
            }
            seqLog.logSequence("SplunkAPI", "splunk", "send", "Success: " + response.statusCode());
            return ToolResponse.ok(Map.of(
                    "status", response.statusCode(),
                    "body", response.body(),
                    "operation", operation,
                    "search", search,
                    "path", path,
                    "authMode", authMode
            ));
        } catch (IOException ex) {
            return splunkExceptionFailure(ex, attempted);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return splunkExceptionFailure(ex, attempted);
        }
    }

    private ToolResponse splunkHttpFailure(HttpResponse<String> response, String attempted) {
        return ToolResponse.failure(
                "SPLUNK_QUERY_FAILED",
                "Splunk returned HTTP " + response.statusCode() + ". attempted: " + attempted
                + ". responseBody=" + truncateBody(response.body())
        );
    }

    private ToolResponse splunkExceptionFailure(Exception exception, String attempted) {
        return ToolResponse.failure(
                "SPLUNK_QUERY_FAILED",
                messageFrom(exception) + ". attempted: " + attempted
        );
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
        if (MODE_SSO.equals(mode) || MODE_SESSION.equals(mode)) {
            return "SPLUNK_SESSION_KEY is required for authMode=sso. Use your SSO login in Splunk, then provide a session key.";
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
        if (MODE_SSO.equals(mode) || MODE_SESSION.equals(mode)) {
            String sessionKey = resolveSessionKey(request, context);
            if (sessionKey == null || sessionKey.isBlank()) {
                return null;
            }
            return "Splunk " + sessionKey;
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

    private String resolveSessionKey(ToolRequest request, ToolContext context) {
        Object payloadSession = request.payload().get("sessionKey");
        if (payloadSession != null && !String.valueOf(payloadSession).isBlank()) {
            return String.valueOf(payloadSession);
        }
        return context.env("SPLUNK_SESSION_KEY");
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

    private HttpRequest buildRequest(String baseUrl, String path, String body, String authHeader) {
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

    private String truncateBody(String body) {
        if (body == null || body.length() <= 600) {
            return body;
        }
        return body.substring(0, 600);
    }
}
