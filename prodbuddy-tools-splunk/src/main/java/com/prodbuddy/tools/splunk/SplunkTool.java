package com.prodbuddy.tools.splunk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
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
    private static final String MODE_COOKIE = "cookie";
    private static final Pattern SESSION_KEY_JSON = Pattern.compile("\"sessionKey\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SESSION_KEY_XML = Pattern.compile("<sessionKey>([^<]+)</sessionKey>");

    private final SplunkOperationGuard guard;
    private final SplunkQueryBuilder queryBuilder;
    private final HttpClient client;
    private final SequenceLogger seqLog;

    public SplunkTool(SplunkOperationGuard guard) {
        this.guard = guard;
        this.queryBuilder = new SplunkQueryBuilder();
        this.client = SplunkHttpClientFactory.buildInsecure();
        this.seqLog = new Slf4jSequenceLogger(SplunkTool.class);
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(NAME, "Splunk search tool", Set.of("splunk.search", "splunk.oneshot", "splunk.jobs", "splunk.results", "splunk.login"));
    }

    @Override
    public boolean supports(ToolRequest request) {
        return NAME.equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        String mode = resolveAuthMode(request, context);
        String op = request.operation().toLowerCase();
        if (!guard.isAllowed(op)) return ToolResponse.failure("SPLUNK_FORBIDDEN", "Forbidden operation");

        String baseUrl = context.env("SPLUNK_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) return ToolResponse.failure("SPLUNK_CONFIG", "SPLUNK_BASE_URL is required");

        if ("login".equals(op)) {
            String cookie = loginAndBuildAuthValue(request, context, baseUrl, true);
            String key = cookie.substring(cookie.indexOf('=') + 1);
            return ToolResponse.ok(Map.of("sessionKey", key, "cookie", cookie, "status", 200));
        }

        String auth;
        try {
            auth = resolveValidAuthHeaderOrThrow(request, context, baseUrl);
        } catch (Exception ex) {
            return SplunkToolHelper.exceptionFailure(ex, "operation=auth");
        }

        String search = queryBuilder.resolveSearch(request, context);
        String path = queryBuilder.resolvePath(op, request.payload());
        String body = queryBuilder.buildBody(op, request.payload(), search);
        String method = resolveMethod(op, request.payload());

        return send(buildRequest(baseUrl, path, body, auth, mode, method, request.payload()), op, search, path, mode, request, context);
    }

    private String resolveMethod(String op, Map<String, Object> payload) {
        Object customMethod = payload.get("method");
        if (customMethod != null && !String.valueOf(customMethod).isBlank()) {
            return String.valueOf(customMethod).toUpperCase();
        }
        if ("results".equals(op) || "jobs".equals(op)) {
            return "GET";
        }
        return "POST";
    }

    private String resolveValidAuthHeaderOrThrow(ToolRequest req, ToolContext ctx, String baseUrl) {
        boolean enabled = resolveAuthEnabled(req, ctx);
        String header = resolveAuthHeader(req, ctx, baseUrl, enabled);
        if (enabled && header == null) throw new RuntimeException(credentialsMessage(req, ctx));
        return header;
    }

    private ToolResponse send(HttpRequest request, String op, String search, String path, String mode, ToolRequest toolRequest, ToolContext context) {
        String attempted = "op=" + op + ", path=" + path + ", mode=" + mode + ", search=" + search;
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (response.statusCode() >= 400 || body.contains("\"type\":\"ERROR\"") || body.contains("\"type\":\"FATAL\"")) {
                return SplunkToolHelper.httpFailure(response, attempted);
            }
            
            final boolean noTruncate = Boolean.parseBoolean(String.valueOf(toolRequest.payload().getOrDefault("noTruncate", "false")));
            final int maxChars = noTruncate ? Integer.MAX_VALUE : Integer.parseInt(String.valueOf(toolRequest.payload().getOrDefault("maxOutputChars", 
                    context.envOrDefault("SPLUNK_MAX_OUTPUT_CHARS", "20000"))));
            
            String finalBody = truncate(body, maxChars);
            return ToolResponse.ok(Map.of(
                "status", response.statusCode(), 
                "body", finalBody, 
                "op", op, 
                "mode", mode,
                "truncated", body != null && body.length() > maxChars
            ));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            return SplunkToolHelper.exceptionFailure(ex, attempted);
        }
    }

    private String truncate(String body, int max) {
        if (body == null || body.length() <= max) {
            return body;
        }
        return body.substring(0, max);
    }

    private String credentialsMessage(ToolRequest req, ToolContext ctx) {
        String mode = resolveAuthMode(req, ctx);
        return switch (mode) {
            case MODE_USER -> "SPLUNK_USERNAME and SPLUNK_PASSWORD are required for user mode";
            case MODE_SSO, MODE_SESSION -> "SPLUNK_SESSION_KEY is required for SSO mode";
            case MODE_COOKIE -> "SPLUNK_AUTH_COOKIE (or credentials) required for cookie mode";
            default -> "SPLUNK_TOKEN is required for token mode";
        };
    }

    private String resolveAuthHeader(ToolRequest req, ToolContext ctx, String baseUrl, boolean enabled) {
        if (!enabled) return null;
        String mode = resolveAuthMode(req, ctx);
        return switch (mode) {
            case MODE_USER -> loginAndBuildAuthValue(req, ctx, baseUrl, false);
            case MODE_SSO, MODE_SESSION -> resolveSsoHeader(req, ctx);
            case MODE_COOKIE -> resolveCookieHeader(req, ctx, baseUrl);
            default -> resolveTokenHeader(req, ctx);
        };
    }

    private String resolveSsoHeader(ToolRequest req, ToolContext ctx) {
        String key = resolveSessionKey(req, ctx);
        return (key == null || key.isBlank()) ? null : "Splunk " + key;
    }

    private String resolveCookieHeader(ToolRequest req, ToolContext ctx, String baseUrl) {
        String cookie = resolveCookie(req, ctx);
        if (cookie != null && !cookie.isBlank()) return cookie;
        return loginAndBuildAuthValue(req, ctx, baseUrl, true);
    }

    private String resolveTokenHeader(ToolRequest req, ToolContext ctx) {
        String token = resolveToken(req, ctx);
        return (token == null || token.isBlank()) ? null : "Splunk " + token;
    }

    private String loginAndBuildAuthValue(ToolRequest req, ToolContext ctx, String baseUrl, boolean isCookie) {
        String user = SplunkToolHelper.resolveCredential("username", "SPLUNK_USERNAME", req, ctx);
        String pass = SplunkToolHelper.resolveCredential("password", "SPLUNK_PASSWORD", req, ctx);
        if (user == null || user.isBlank() || pass == null || pass.isBlank()) return null;

        HttpRequest loginReq = buildLoginRequest(baseUrl, user, pass);
        try {
            HttpResponse<String> response = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
            String key = SplunkToolHelper.extractSessionKey(response.body(), SESSION_KEY_JSON, SESSION_KEY_XML);
            if (key == null || key.isBlank()) {
                throw new RuntimeException("Splunk auth failed. HTTP " + response.statusCode() + ": " + response.body());
            }
            return isCookie ? "splunkd_" + SplunkToolHelper.extractPort(baseUrl) + "=" + key : "Splunk " + key;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Splunk connectivity error: " + ex.getMessage(), ex);
        }
    }

    private HttpRequest buildLoginRequest(String baseUrl, String user, String pass) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/services/auth/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(SplunkToolHelper.buildLoginBody(user, pass)))
                .build();
    }

    private String resolveAuthMode(ToolRequest req, ToolContext ctx) {
        return String.valueOf(req.payload().getOrDefault("authMode", ctx.envOrDefault("SPLUNK_AUTH_MODE", MODE_TOKEN))).toLowerCase();
    }

    private String resolveToken(ToolRequest req, ToolContext ctx) {
        Object val = req.payload().get("token");
        return (val != null && !String.valueOf(val).isBlank()) ? String.valueOf(val) : ctx.env("SPLUNK_TOKEN");
    }

    private String resolveCookie(ToolRequest req, ToolContext ctx) {
        Object val = req.payload().get("cookie");
        return (val != null && !String.valueOf(val).isBlank()) ? String.valueOf(val) : ctx.env("SPLUNK_AUTH_COOKIE");
    }

    private String resolveSessionKey(ToolRequest req, ToolContext ctx) {
        Object val = req.payload().get("sessionKey");
        return (val != null && !String.valueOf(val).isBlank()) ? String.valueOf(val) : ctx.env("SPLUNK_SESSION_KEY");
    }

    private boolean resolveAuthEnabled(ToolRequest req, ToolContext ctx) {
        return Boolean.parseBoolean(String.valueOf(req.payload().getOrDefault("authEnabled", ctx.envOrDefault("SPLUNK_AUTH_ENABLED", "true"))));
    }

    private HttpRequest buildRequest(String base, String path, String body, String auth, String mode, String method, Map<String, Object> payload) {
        HttpRequest.Builder b = HttpRequest.newBuilder().timeout(Duration.ofSeconds(20));
        
        if ("GET".equalsIgnoreCase(method)) {
            String url = body.isBlank() ? base + path : base + path + "?" + body;
            b.uri(URI.create(url)).GET();
        } else {
            b.uri(URI.create(base + path))
             .header("Content-Type", "application/x-www-form-urlencoded")
             .POST(HttpRequest.BodyPublishers.ofString(body));
        }

        if (auth != null && !auth.isBlank()) {
            if (MODE_COOKIE.equals(mode)) b.header("Cookie", auth);
            else b.header("Authorization", auth);
        }

        Object headers = payload.get("headers");
        if (headers instanceof Map<?, ?> headerMap) {
            headerMap.forEach((k, v) -> b.header(String.valueOf(k), String.valueOf(v)));
        }

        return b.build();
    }
}
