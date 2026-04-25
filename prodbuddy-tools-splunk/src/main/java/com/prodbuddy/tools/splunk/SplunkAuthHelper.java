package com.prodbuddy.tools.splunk;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for Splunk authentication.
 */
final class SplunkAuthHelper {

    private SplunkAuthHelper() { }

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
     * Logins and builds auth value.
     * @param client HTTP client.
     * @param req Tool request.
     * @param ctx Tool context.
     * @param baseUrl Base URL.
     * @param isCookie true if cookie.
     * @param jsonPattern JSON pattern.
     * @param xmlPattern XML pattern.
     * @param timeout Timeout.
     * @return Auth value.
     */
    static String loginAndBuildAuthValue(
            final HttpClient client, final ToolRequest req,
            final ToolContext ctx, final String baseUrl,
            final boolean isCookie, final Pattern jsonPattern,
            final Pattern xmlPattern, final int timeout) {
        String user = resolveCredential("username",
                "SPLUNK_USERNAME", req, ctx);
        String pass = resolveCredential("password",
                "SPLUNK_PASSWORD", req, ctx);
        if (user == null || pass == null) {
            return null;
        }
        try {
            String body = executeLogin(client, baseUrl, user, pass, timeout);
            String key = extractSessionKey(body, jsonPattern, xmlPattern);
            if (key == null) {
                throw new RuntimeException("Splunk auth failed: " + body);
            }
            return isCookie ? "splunkd_" + SplunkToolHelper.extractPort(baseUrl)
                    + "=" + key : "Splunk " + key;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Splunk error: " + ex.getMessage(), ex);
        }
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
}
