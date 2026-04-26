package com.prodbuddy.tools.splunk;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
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
        String code = ex instanceof java.io.IOException
                ? "SPLUNK_IO_ERROR" : "SPLUNK_ERROR";
        return ToolResponse.failure(code,
                ex.getMessage() + " (attempted=" + attempted + ")",
                java.util.Map.of("body", String.valueOf(ex.getMessage()))
        );
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
     * Builds response data map.
     * @param response HTTP response.
     * @param op Operation.
     * @param mode Mode.
     * @param toolRequest Tool request.
     * @param context Tool context.
     * @return Data map.
     */
    static Map<String, Object> buildResponseData(
            final HttpResponse<String> response, final String op,
            final String mode, final ToolRequest toolRequest,
            final ToolContext context) {
        final boolean noTrunc = Boolean.parseBoolean(String.valueOf(
                toolRequest.payload().getOrDefault("noTruncate", "false")));
        final int maxChars = noTrunc ? Integer.MAX_VALUE : Integer.parseInt(
                String.valueOf(toolRequest.payload().getOrDefault(
                        "maxOutputChars", context.envOrDefault(
                                "SPLUNK_MAX_OUTPUT_CHARS", "20000"))));
        String body = response.body();
        String finalBody = (body != null && body.length() > maxChars)
                ? body.substring(0, maxChars) : body;
        Map<String, Object> data = new HashMap<>();
        data.put("status", response.statusCode());
        data.put("body", finalBody);
        data.put("op", op);
        data.put("mode", mode);
        data.put("truncated", body != null && body.length() > maxChars);
        String sid = extractSid(body);
        if (sid != null) {
            data.put("sid", sid);
        }
        return data;
    }

    /**
     * Applies auth and headers to request builder.
     * @param b Builder.
     * @param auth Auth value.
     * @param mode Mode.
     * @param payload Payload.
     */
    static void applyAuthAndHeaders(final HttpRequest.Builder b,
                                     final String auth, final String mode,
                                     final Map<String, Object> payload) {
        if (auth != null && !auth.isBlank()) {
            b.header("cookie".equals(mode) ? "Cookie" : "Authorization",
                    auth);
        }
        Object headers = payload.get("headers");
        if (headers instanceof Map<?, ?> headerMap) {
            headerMap.forEach((k, v) -> b.header(String.valueOf(k),
                    String.valueOf(v)));
        }
    }
}
