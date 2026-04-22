package com.prodbuddy.tools.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

public final class GraphQLClient {
    private final HttpClient client;
    private final ObjectMapper mapper;

    public GraphQLClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    public String execute(String url, String query, Map<String, Object> variables, Map<String, Object> auth) throws Exception {
        Map<String, Object> bodyMap = Map.of(
                "query", query,
                "variables", variables == null ? Map.of() : variables
        );
        String body = mapper.writeValueAsString(bodyMap);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        applyAuth(builder, auth);

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException("GraphQL request failed with status " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    private void applyAuth(HttpRequest.Builder builder, Map<String, Object> auth) {
        if (auth == null) return;

        String mode = String.valueOf(auth.getOrDefault("mode", "none")).toLowerCase();
        String token = String.valueOf(auth.getOrDefault("token", ""));

        switch (mode) {
            case "bearer":
                builder.header("Authorization", "Bearer " + token);
                break;
            case "basic":
                String user = String.valueOf(auth.get("username"));
                String pass = String.valueOf(auth.get("password"));
                String encoded = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
                builder.header("Authorization", "Basic " + encoded);
                break;
            case "cookie":
                builder.header("Cookie", token);
                break;
            case "header":
                String name = String.valueOf(auth.getOrDefault("headerName", "X-GraphQL-Token"));
                builder.header(name, token);
                break;
        }
    }
}
