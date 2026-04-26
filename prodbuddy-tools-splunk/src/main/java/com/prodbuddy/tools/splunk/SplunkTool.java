package com.prodbuddy.tools.splunk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.prodbuddy.core.system.QueryService;
import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

/** Splunk search tool implementation. */
public final class SplunkTool implements Tool {
    /** Tool name. */
    private static final String NAME = "splunk";
    /** Token auth mode. */
    private static final String MODE_TOKEN = "token";
    /** User auth mode. */
    private static final String MODE_USER = "user";
    /** SSO auth mode. */
    private static final String MODE_SSO = "sso";
    /** Session auth mode. */
    private static final String MODE_SESSION = "session";
    /** Cookie auth mode. */
    private static final String MODE_COOKIE = "cookie";
    /** Session key JSON pattern. */
    private static final Pattern SESSION_KEY_JSON = Pattern.compile(
            "\"sessionKey\"\\s*:\\s*\"([^\"]+)\"");
    /** Session key XML pattern. */
    private static final Pattern SESSION_KEY_XML = Pattern.compile(
            "<sessionKey>([^<]+)</sessionKey>");

    /** Default timeout. */
    private static final int DEFAULT_TIMEOUT = 20;
    /** HTTP OK. */
    private static final int HTTP_OK = 200;
    /** HTTP Bad Request. */
    private static final int HTTP_BAD_REQUEST = 400;

    /** Operation guard. */
    private final SplunkOperationGuard guard;
    /** Query builder. */
    private final SplunkQueryBuilder queryBuilder;
    /** Query service. */
    private final QueryService queryService;
    /** HTTP client. */
    private final HttpClient client;
    /** Sequence logger. */
    private final SequenceLogger seqLog;

    /**
     * Constructor.
     * @param operationGuard Operation guard.
     */
    public SplunkTool(final SplunkOperationGuard operationGuard) {
        this(operationGuard, SplunkHttpClientFactory.buildInsecure(),
                new QueryService());
    }

    /**
     * Protected constructor for testing.
     * @param operationGuard Operation guard.
     * @param httpClient HTTP client.
     * @param qs Query service.
     */
    protected SplunkTool(final SplunkOperationGuard operationGuard,
                         final HttpClient httpClient,
                         final QueryService qs) {
        this.guard = operationGuard;
        this.queryService = qs;
        this.queryBuilder = new SplunkQueryBuilder(qs);
        this.client = httpClient;
        this.seqLog = com.prodbuddy.observation.ObservationContext.getLogger();
    }

    /**
     * Protected constructor for testing.
     * @param operationGuard Operation guard.
     * @param httpClient HTTP client.
     */
    protected SplunkTool(final SplunkOperationGuard operationGuard,
                         final HttpClient httpClient) {
        this(operationGuard, httpClient, new QueryService());
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(NAME, "Splunk search tool", Set.of(
                "splunk.search", "splunk.oneshot", "splunk.jobs",
                "splunk.results", "splunk.login"));
    }

    @Override
    public boolean supports(final ToolRequest request) {
        return NAME.equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(final ToolRequest request,
                                final ToolContext context) {
        String op = request.operation().toLowerCase();
        if (!guard.isAllowed(op)) {
            return ToolResponse.failure("SPLUNK_FORBIDDEN", "Forbidden");
        }

        String baseUrl = context.env("SPLUNK_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            return ToolResponse.failure("SPLUNK_CONFIG", "Base URL required");
        }

        if ("login".equals(op)) {
            String cookie = loginAndBuildAuthValue(request, context,
                    baseUrl, true);
            String key = cookie.substring(cookie.indexOf('=') + 1);
            return ToolResponse.ok(Map.of("sessionKey", key, "cookie", cookie,
                    "status", HTTP_OK));
        }

        return performOperation(request, context, baseUrl, op);
    }

    private ToolResponse handleExecute(final ToolRequest request,
                                       final ToolContext context) {
        String base = SplunkToolHelper.resolveValue(request, context,
                "baseUrl", "SPLUNK_BASE_URL");
        if (base == null || base.isBlank()) {
            return ToolResponse.failure("SPLUNK_BASE_URL", "base required");
        }
        String op = String.valueOf(request.payload().getOrDefault("operation",
                "search/jobs"));
        String search = queryBuilder.resolveSearch(request, context);
        String path = "servicesNS/-/-/" + op;
        boolean authEnabled = SplunkAuthHelper.resolveAuthEnabled(request,
                context);
        String authMode = SplunkAuthHelper.resolveAuthMode(request, context,
                MODE_TOKEN);
        String auth = authEnabled ? resolveValidAuthHeaderOrThrow(request,
                context, base) : null;
        String method = "search/jobs/export".equals(op) ? "GET" : "POST";
        String body = "search=" + URLEncoder.encode(search,
                StandardCharsets.UTF_8);
        HttpRequest req = buildRequest(base, path, body, auth, authMode,
                method, request.payload());
        return send(req, op, search, path, authMode, request, context);
    }

    ToolResponse performOperation(final ToolRequest request,
                                  final ToolContext context,
                                  final String baseUrl,
                                  final String op) {
        String mode = SplunkAuthHelper.resolveAuthMode(request, context,
                MODE_TOKEN);
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

        try {
            seqLog.logSequence("agent", "splunk", op, "Executing Splunk Search", 
                Map.of("type", "note", "noteText", "Search: " + search));
            HttpRequest httpRequest = buildRequest(baseUrl, path, body, auth,
                    mode, method, request.payload());
            return send(httpRequest, op, search, path, mode, request, context);
        } catch (IllegalArgumentException e) {
            return ToolResponse.failure("SPLUNK_URL_ERROR", e.getMessage());
        }
    }

    private String resolveMethod(final String op,
                                 final Map<String, Object> payload) {
        Object customMethod = payload.get("method");
        if (customMethod != null && !String.valueOf(customMethod).isBlank()) {
            return String.valueOf(customMethod).toUpperCase();
        }
        return ("results".equals(op) || "jobs".equals(op)) ? "GET" : "POST";
    }
    String resolveValidAuthHeaderOrThrow(
            final ToolRequest req, final ToolContext ctx,
            final String baseUrl) {
        boolean enabled = SplunkAuthHelper.resolveAuthEnabled(req, ctx);
        if (!enabled) {
            return null;
        }
        String mode = SplunkAuthHelper.resolveAuthMode(req, ctx, MODE_TOKEN);
        String header = switch (mode) {
            case MODE_USER -> loginAndBuildAuthValue(req, ctx, baseUrl, false);
            case MODE_SSO, MODE_SESSION -> resolveHeader(req, ctx, "sessionKey",
                    "SPLUNK_SESSION_KEY");
            case MODE_COOKIE -> resolveCookieHeader(req, ctx, baseUrl);
            default -> resolveHeader(req, ctx, "token", "SPLUNK_TOKEN");
        };
        if (header == null) {
            throw new RuntimeException("Missing credentials for " + mode);
        }
        return header;
    }
    private String resolveHeader(final ToolRequest req, final ToolContext ctx,
                                 final String pKey, final String eKey) {
        String val = SplunkToolHelper.resolveValue(req, ctx, pKey, eKey);
        return (val == null || val.isBlank()) ? null : "Splunk " + val;
    }
    private String resolveCookieHeader(final ToolRequest req,
                                       final ToolContext ctx,
                                       final String baseUrl) {
        String c = SplunkToolHelper.resolveValue(req, ctx, "cookie",
                "SPLUNK_AUTH_COOKIE");
        return (c != null && !c.isBlank()) ? c
                : loginAndBuildAuthValue(req, ctx, baseUrl, true);
    }
    private ToolResponse send(
            final HttpRequest request, final String op, final String search,
            final String path, final String mode, final ToolRequest toolRequest,
            final ToolContext context) {
        String attempted = "op=" + op + ", path=" + path + ", search=" + search;
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (response.statusCode() >= HTTP_BAD_REQUEST
                    || body.contains("\"type\":\"ERROR\"")
                    || body.contains("\"type\":\"FATAL\"")) {
                return SplunkToolHelper.httpFailure(response, attempted);
            }
            return ToolResponse.ok(SplunkToolHelper.buildResponseData(
                    response, op, mode, toolRequest, context));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return SplunkToolHelper.exceptionFailure(ex, attempted);
        }
    }

    private String loginAndBuildAuthValue(
            final ToolRequest req, final ToolContext ctx,
            final String baseUrl, final boolean isCookie) {
        return SplunkAuthHelper.loginAndBuildAuthValue(client, req, ctx,
                baseUrl, isCookie, SESSION_KEY_JSON, SESSION_KEY_XML,
                DEFAULT_TIMEOUT);
    }

    private HttpRequest buildRequest(
            final String base, final String path, final String body,
            final String auth, final String mode, final String method,
            final Map<String, Object> payload) {
        String url = buildUrl(base, path, body, method);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT));
        if (!"GET".equalsIgnoreCase(method)) {
            b.header("Content-Type", "application/x-www-form-urlencoded")
             .POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.GET();
        }
        SplunkToolHelper.applyAuthAndHeaders(b, auth, mode, payload);
        return b.build();
    }

    private String buildUrl(final String base, final String path,
                             final String body, final String method) {
        String fullPath = path.startsWith("/") ? path : "/" + path;
        String url;
        if ("GET".equalsIgnoreCase(method) && !body.isBlank()) {
            String separator = fullPath.contains("?") ? "&" : "?";
            url = base + fullPath + separator + body;
        } else {
            url = base + fullPath;
        }
        if (url.contains("${")) {
            throw new IllegalArgumentException("Unresolved variables in URL: "
                    + url);
        }
        return url;
    }
}
