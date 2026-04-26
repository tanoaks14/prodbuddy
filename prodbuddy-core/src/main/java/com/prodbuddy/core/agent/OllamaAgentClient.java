package com.prodbuddy.core.agent;

import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OllamaAgentClient {

    private static final int REQUEST_TIMEOUT_SEC = 60;
    private final HttpClient client;
    private final SequenceLogger seqLog;

    public OllamaAgentClient() {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.seqLog = new Slf4jSequenceLogger(OllamaAgentClient.class);
    }

    /**
     * Generate response from prompt.
     * @param prompt the text prompt
     * @param config the agent config
     * @return the LLM response
     */
    public String generate(final String prompt, final AgentConfig config) {
        return generate(prompt, java.util.Collections.emptyList(), config);
    }

    /**
     * Generate response from prompt with images.
     * @param prompt the text prompt
     * @param images list of base64 image strings
     * @param config the agent config
     * @return the LLM response
     */
    public String generate(final String prompt,
                           final java.util.List<String> images,
                           final AgentConfig config) {
        seqLog.logSequence("Client", "OllamaAgentClient", "generate",
                "Sending prompt to LLM (multimodal=" + !images.isEmpty() + ")");
        String body = buildRequestBody(prompt, images, config);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + config.chatPath()))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (config.authEnabled() && !config.apiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiKey());
        }
        return executeRequest(builder.build());
    }

    private String buildRequestBody(final String prompt,
                                    final java.util.List<String> images,
                                    final AgentConfig config) {
        String effectivePrompt = withFunctionCallingInstructions(prompt, config);
        boolean thinking = config.thinkingEnabled()
                || (config.functionCallingEnabled()
                && config.functionCallingWithThinking());

        StringBuilder body = new StringBuilder();
        body.append("{")
            .append("\"model\":\"").append(escapeJson(config.model())).append("\",")
            .append("\"prompt\":\"").append(escapeJson(effectivePrompt)).append("\",")
            .append("\"stream\":").append(config.streamEnabled()).append(",")
            .append("\"think\":").append(thinking);

        if (!images.isEmpty()) {
            body.append(",\"images\":[").append(String.join(",", images.stream()
                .map(i -> "\"" + i + "\"").toList())).append("]");
        }
        return body.append("}").toString();
    }

    private String executeRequest(final HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            seqLog.logSequence("OllamaAgentClient", "Client", "execute",
                    "LLM Responded: " + response.statusCode());
            return normalizeResponseBody(response.body());
        } catch (IOException | InterruptedException exception) {
            seqLog.logSequence("OllamaAgentClient", "Client", "execute",
                    "LLM Failed");
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Unable to call Ollama agent", exception);
        }
    }

    private static String normalizeResponseBody(String body) {
        String streamed = parseStreamedResponse(body);
        if (!streamed.isBlank()) {
            return streamed;
        }
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

    private static String parseStreamedResponse(String body) {
        if (body == null || body.isBlank() || !body.contains("\n")) {
            return "";
        }
        StringBuilder chunks = new StringBuilder();
        for (String line : body.split("\\n")) {
            String part = extractJsonString(line, "\"response\":\"");
            if (!part.isBlank()) {
                chunks.append(part);
            }
        }
        return chunks.toString().trim();
    }

    private static String withFunctionCallingInstructions(String prompt, AgentConfig config) {
        if (!config.functionCallingEnabled()) {
            return prompt;
        }
        return "When a tool call is needed, return only compact JSON with fields tool_name, operation, and payload. "
                + "Otherwise answer normally."
                + (config.functionCallingWithThinking() ? " You may reason internally before emitting the tool-call JSON." : "")
                + "\nUser request:\n" + prompt;
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
