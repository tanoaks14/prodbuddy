package com.prodbuddy.tools.splunk;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.net.URI;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to offload logic and formatting from SplunkTool.
 */
final class SplunkToolHelper {

    /** Default truncation length for error bodies. */
    private static final int DEFAULT_TRUNCATE_LEN = 600;
    /** Default Splunk port. */
    private static final String DEFAULT_SPLUNK_PORT = "8089";

    private SplunkToolHelper() { }

    /**
     * Formats an HTTP failure response.
     * @param response HTTP response.
     * @param attempted Attempted operation description.
     * @return ToolResponse failure.
     */
    static ToolResponse httpFailure(
            final HttpResponse<String> response, final String attempted) {
        return ToolResponse.failure(
                "SPLUNK_QUERY_FAILED",
                "Splunk returned HTTP " + response.statusCode()
                        + ". attempted: " + attempted
                        + ". responseBody=" + truncate(response.body()),
                java.util.Map.of("body", response.body())
        );
    }

    /**
     * Formats an exception failure response.
     * @param ex Exception.
     * @param attempted Attempted operation description.
     * @return ToolResponse failure.
     */
    static ToolResponse exceptionFailure(final Exception ex,
                                         final String attempted) {
        return ToolResponse.failure(
                ex instanceof java.io.IOException ? "SPLUNK_IO_ERROR" : "SPLUNK_ERROR",
                ex.getMessage() + " (attempted=" + attempted + ")",
                java.util.Map.of("body", String.valueOf(ex.getMessage()))
        );
    }

    /**
     * Resolves a credential from payload or environment.
     * @param payloadKey Key in payload.
     * @param envKey Key in environment.
     * @param request Tool request.
     * @param context Tool context.
     * @return Resolved credential.
     */
    static String resolveCredential(
            final String payloadKey, final String envKey,
            final ToolRequest request, final ToolContext context) {
        Object payloadValue = request.payload().get(payloadKey);
        if (payloadValue != null && !String.valueOf(payloadValue).isBlank()) {
            return String.valueOf(payloadValue);
        }
        return context.env(envKey);
    }

    /**
     * Builds the login body.
     * @param username Splunk username.
     * @param password Splunk password.
     * @return URL-encoded login body.
     */
    static String buildLoginBody(final String username, final String password) {
        return "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password,
                StandardCharsets.UTF_8) + "&output_mode=json";
    }

    /**
     * Extracts session key from response body.
     * @param body Response body.
     * @param jsonPattern JSON pattern.
     * @param xmlPattern XML pattern.
     * @return Session key or null.
     */
    static String extractSessionKey(
            final String body, final Pattern jsonPattern,
            final Pattern xmlPattern) {
        if (body == null || body.isBlank()) {
            return null;
        }
        Matcher json = jsonPattern.matcher(body);
        if (json.find()) {
            return json.group(1);
        }
        Matcher xml = xmlPattern.matcher(body);
        if (xml.find()) {
            return xml.group(1);
        }
        return null;
    }

    /**
     * Extracts SID from response body.
     * @param body Response body.
     * @return SID or null.
     */
    static String extractSid(final String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        // Match "sid":"..." or <sid>...</sid>
        Pattern json = Pattern.compile("\"sid\"\\s*:\\s*\"([^\"]+)\"");
        Pattern xml = Pattern.compile("<sid>([^<]+)</sid>");

        Matcher jm = json.matcher(body);
        if (jm.find()) {
            return jm.group(1);
        }
        Matcher xm = xml.matcher(body);
        if (xm.find()) {
            return xm.group(1);
        }
        return null;
    }

    /**
     * Extracts the port from a base URL.
     * @param baseUrl Base URL.
     * @return Port string.
     */
    static String extractPort(final String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            int port = uri.getPort();
            return port == -1 ? DEFAULT_SPLUNK_PORT : String.valueOf(port);
        } catch (Exception e) {
            return DEFAULT_SPLUNK_PORT;
        }
    }

    /**
     * Truncates a body string.
     * @param body Body string.
     * @return Truncated string.
     */
    static String truncate(final String body) {
        if (body == null || body.length() <= DEFAULT_TRUNCATE_LEN) {
            return body;
        }
        return body.substring(0, DEFAULT_TRUNCATE_LEN);
    }

    /**
     * Resolves the authentication mode.
     * @param req Tool request.
     * @param ctx Tool context.
     * @param defaultMode Default mode.
     * @return Resolved mode.
     */
    static String resolveAuthMode(final ToolRequest req, final ToolContext ctx,
                                  final String defaultMode) {
        return String.valueOf(req.payload().getOrDefault("authMode",
                ctx.envOrDefault("SPLUNK_AUTH_MODE", defaultMode)))
                .toLowerCase();
    }

    /**
     * Resolves a value from payload or environment.
     * @param req Tool request.
     * @param ctx Tool context.
     * @param payloadKey Key in payload.
     * @param envKey Key in environment.
     * @return Resolved value.
     */
    static String resolveValue(final ToolRequest req, final ToolContext ctx,
                               final String payloadKey, final String envKey) {
        Object val = req.payload().get(payloadKey);
        return (val != null && !String.valueOf(val).isBlank())
                ? String.valueOf(val) : ctx.env(envKey);
    }

    /**
     * Resolves if authentication is enabled.
     * @param req Tool request.
     * @param ctx Tool context.
     * @return true if enabled.
     */
    static boolean resolveAuthEnabled(final ToolRequest req,
                                      final ToolContext ctx) {
        return Boolean.parseBoolean(String.valueOf(req.payload().getOrDefault(
                "authEnabled", ctx.envOrDefault("SPLUNK_AUTH_ENABLED",
                        "true"))));
    }

    /**
     * Executes login.
     * @param client HTTP client.
     * @param baseUrl Base URL.
     * @param user Username.
     * @param pass Password.
     * @param timeout Timeout.
     * @return Response body.
     * @throws IOException on error.
     * @throws InterruptedException on error.
     */
    static String executeLogin(final HttpClient client, final String baseUrl,
                               final String user, final String pass,
                               final int timeout)
            throws IOException, InterruptedException {
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/services/auth/login"))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildLoginBody(user, pass)))
                .build();
        return client.send(loginReq, HttpResponse.BodyHandlers.ofString())
                .body();
    }
}
