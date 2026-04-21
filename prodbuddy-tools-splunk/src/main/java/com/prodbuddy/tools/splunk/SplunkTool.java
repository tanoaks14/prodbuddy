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

        return send(buildRequest(baseUrl, path, body, auth, mode), op, search, path, mode);
    }

    private String resolveValidAuthHeaderOrThrow(ToolRequest req, ToolContext ctx, String baseUrl) {
        boolean enabled = resolveAuthEnabled(req, ctx);
        String header = resolveAuthHeader(req, ctx, baseUrl, enabled);
        if (enabled && header == null) throw new RuntimeException(credentialsMessage(req, ctx));
        return header;
    }

    private ToolResponse send(HttpRequest request, String op, String search, String path, String mode) {
        String attempted = "op=" + op + ", path=" + path + ", mode=" + mode + ", search=" + search;
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (response.statusCode() >= 400 || body.contains("\"type\":\"ERROR\"") || body.contains("\"type\":\"FATAL\"")) {
                return SplunkToolHelper.httpFailure(response, attempted);
            }
            return ToolResponse.ok(Map.of("status", response.statusCode(), "body", body, "op", op, "mode", mode));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            return SplunkToolHelper.exceptionFailure(ex, attempted);
        }
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

    private HttpRequest buildRequest(String base, String path, String body, String auth, String mode) {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(base + path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));
        if (auth != null && !auth.isBlank()) {
            if (MODE_COOKIE.equals(mode)) b.header("Cookie", auth);
            else b.header("Authorization", auth);
        }
        return b.build();
    }
}
