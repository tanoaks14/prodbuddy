package com.prodbuddy.app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OllamaAgentClient {

    private final HttpClient client;

    public OllamaAgentClient() {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public String generate(String prompt, AgentConfig config) {
        String body = "{\"model\":\"" + escapeJson(config.model()) + "\",\"prompt\":\""
                + escapeJson(prompt) + "\",\"stream\":false}";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + config.chatPath()))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (config.authEnabled() && !config.apiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiKey());
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return normalizeResponseBody(response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Unable to call Ollama agent", exception);
        }
    }

    private static String normalizeResponseBody(String body) {
        String candidate = extractJsonString(body, "\"response\":\"");
        if (!candidate.isBlank()) {
            return candidate;
        }
        String content = extractJsonString(body, "\"content\":\"");
        if (!content.isBlank()) {
            return content;
        }
        return body;
    }

    private static String extractJsonString(String body, String marker) {
        if (body == null || body.isBlank()) {
            return "";
        }
        int start = body.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int from = start + marker.length();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int index = from; index < body.length(); index++) {
            char current = body.charAt(index);
            if (escaped) {
                value.append(unescape(current));
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return value.toString().trim();
            }
            value.append(current);
        }
        return "";
    }

    private static char unescape(char value) {
        return switch (value) {
            case 'n' ->
                '\n';
            case 'r' ->
                '\r';
            case 't' ->
                '\t';
            case '"' ->
                '"';
            case '\\' ->
                '\\';
            default ->
                value;
        };
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
