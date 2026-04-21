package com.prodbuddy.tools.splunk;

import com.prodbuddy.core.tool.ToolError;
import com.prodbuddy.core.tool.ToolResponse;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Utility class to offload error formatting and string manipulation from SplunkTool
 * to satisfy strict FileLength constraints.
 */
final class SplunkToolHelper {

    private SplunkToolHelper() {}

    static ToolResponse httpFailure(HttpResponse<String> response, String attempted) {
        return ToolResponse.failure(
                "SPLUNK_QUERY_FAILED",
                "Splunk returned HTTP " + response.statusCode() + ". attempted: " + attempted
                        + ". responseBody=" + truncate(response.body())
        );
    }

    static ToolResponse exceptionFailure(Exception ex, String attempted) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex.getClass().getSimpleName();
        }
        return ToolResponse.failure("SPLUNK_QUERY_FAILED", msg + ". attempted: " + attempted);
    }

    static String resolveCredential(String payloadKey, String envKey, com.prodbuddy.core.tool.ToolRequest request, com.prodbuddy.core.tool.ToolContext context) {
        Object payloadValue = request.payload().get(payloadKey);
        if (payloadValue != null && !String.valueOf(payloadValue).isBlank()) {
            return String.valueOf(payloadValue);
        }
        return context.env(envKey);
    }

    static String buildLoginBody(String username, String password) {
        return "username=" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8)
                + "&password=" + java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8)
                + "&output_mode=json";
    }

    static String extractSessionKey(String body, java.util.regex.Pattern jsonPattern, java.util.regex.Pattern xmlPattern) {
        if (body == null || body.isBlank()) {
            return null;
        }
        java.util.regex.Matcher json = jsonPattern.matcher(body);
        if (json.find()) {
            return json.group(1);
        }
        java.util.regex.Matcher xml = xmlPattern.matcher(body);
        if (xml.find()) {
            return xml.group(1);
        }
        return null;
    }

    static String extractPort(String baseUrl) {
        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            int port = uri.getPort();
            return port == -1 ? "8089" : String.valueOf(port);
        } catch (Exception e) {
            return "8089";
        }
    }

    static String truncate(String body) {
        if (body == null || body.length() <= 600) {
            return body;
        }
        return body.substring(0, 600);
    }
}
