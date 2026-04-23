package com.prodbuddy.tools.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/** Client for executing GraphQL queries. */
public final class GraphQLClient {

    /** Connection timeout. */
    private static final int CONNECT_TIMEOUT = 10;
    /** Request timeout. */
    private static final int REQUEST_TIMEOUT = 30;
    /** Bad request status. */
    private static final int HTTP_BAD_REQUEST = 400;

    /** HTTP client. */
    private final HttpClient client;
    /** JSON mapper. */
    private final ObjectMapper mapper;

    /** Constructor. */
    public GraphQLClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Executes a GraphQL query.
     * @param url Server URL.
     * @param query GraphQL query string.
     * @param variables Query variables.
     * @param auth Authentication map.
     * @return Response body.
     * @throws Exception on error.
     */
    public String execute(final String url, final String query,
                          final Map<String, Object> variables,
                          final Map<String, Object> auth) throws Exception {
        Map<String, Object> bodyMap = Map.of(
                "query", query,
                "variables", variables == null ? Map.of() : variables
        );
        String body = mapper.writeValueAsString(bodyMap);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        applyAuth(builder, auth);

        HttpResponse<String> response = client.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= HTTP_BAD_REQUEST) {
            throw new RuntimeException("GraphQL request failed status "
                    + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    private void applyAuth(final HttpRequest.Builder builder,
                           final Map<String, Object> auth) {
        if (auth == null) {
            return;
        }

        String mode = String.valueOf(auth.getOrDefault("mode", "none"))
                .toLowerCase();
        String token = String.valueOf(auth.getOrDefault("token", ""));

        switch (mode) {
            case "bearer":
                builder.header("Authorization", "Bearer " + token);
                break;
            case "basic":
                applyBasic(builder, auth);
                break;
            case "cookie":
                builder.header("Cookie", token);
                break;
            case "header":
                applyHeader(builder, auth, token);
                break;
            default:
                break;
        }
    }

    private void applyBasic(final HttpRequest.Builder builder,
                            final Map<String, Object> auth) {
        String user = String.valueOf(auth.get("username"));
        String pass = String.valueOf(auth.get("password"));
        String encoded = Base64.getEncoder().encodeToString(
                (user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        builder.header("Authorization", "Basic " + encoded);
    }

    private void applyHeader(final HttpRequest.Builder builder,
                             final Map<String, Object> auth,
                             final String token) {
        String name = String.valueOf(auth.getOrDefault("headerName",
                "X-GraphQL-Token"));
        builder.header(name, token);
    }
}
