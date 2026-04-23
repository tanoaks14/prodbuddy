package com.prodbuddy.tools.graphql;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public final class GraphQLTool implements Tool {
    private static final String NAME = "graphql";
    private final GraphQLClient client;
    private final ObjectMapper mapper;

    public GraphQLTool() {
        this.client = new GraphQLClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
            NAME,
            "Generic GraphQL tool for querying and schema exploration",
            Set.of("graphql.query", "graphql.introspect", "graphql.list_operations")
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        return NAME.equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        String url = String.valueOf(request.payload().getOrDefault("url", ""));
        if (url.isBlank()) {
            return ToolResponse.failure("MISSING_URL", "GraphQL endpoint URL is required");
        }

        Map<String, Object> auth = (Map<String, Object>) request.payload().get("auth");

        return switch (request.operation()) {
            case "query" -> handleQuery(request, url, auth);
            case "introspect" -> handleIntrospect(url, auth);
            case "list_operations" -> handleListOperations(url, auth);
            default -> ToolResponse.failure("UNSUPPORTED_OP", "Operation not supported: " + request.operation());
        };
    }

    private ToolResponse handleQuery(ToolRequest request, String url, Map<String, Object> auth) {
        String query = String.valueOf(request.payload().getOrDefault("query", ""));
        Map<String, Object> variables = (Map<String, Object>) request.payload().get("variables");

        try {
            String response = client.execute(url, query, variables, auth);
            
            final boolean noTruncate = Boolean.parseBoolean(String.valueOf(request.payload().getOrDefault("noTruncate", "false")));
            final int maxChars = noTruncate ? Integer.MAX_VALUE : Integer.parseInt(String.valueOf(request.payload().getOrDefault("maxOutputChars", "20000")));
            
            String finalResponse = response;
            if (response != null && response.length() > maxChars) {
                finalResponse = response.substring(0, maxChars);
            }

            return ToolResponse.ok(Map.of(
                "data", mapper.readTree(finalResponse),
                "truncated", response != null && response.length() > maxChars
            ));
        } catch (Exception e) {
            return ToolResponse.failure("GQL_QUERY_ERROR", e.getMessage());
        }
    }

    private ToolResponse handleIntrospect(String url, Map<String, Object> auth) {
        try {
            String response = client.execute(url, IntrospectionQueryBuilder.getFullIntrospectionQuery(), null, auth);
            return ToolResponse.ok(Map.of("schema", mapper.readTree(response)));
        } catch (Exception e) {
            return ToolResponse.failure("GQL_INTROSPECT_ERROR", e.getMessage());
        }
    }

    private ToolResponse handleListOperations(String url, Map<String, Object> auth) {
        try {
            String response = client.execute(url, IntrospectionQueryBuilder.getOperationsSummaryQuery(), null, auth);
            JsonNode root = mapper.readTree(response);
            JsonNode schema = root.path("data").path("__schema");

            Map<String, Object> result = new HashMap<>();
            result.put("queries", extractFields(schema.path("queryType")));
            result.put("mutations", extractFields(schema.path("mutationType")));

            return ToolResponse.ok(result);
        } catch (Exception e) {
            return ToolResponse.failure("GQL_LIST_ERROR", e.getMessage());
        }
    }

    private List<Map<String, String>> extractFields(JsonNode typeNode) {
        List<Map<String, String>> fields = new ArrayList<>();
        if (typeNode.isMissingNode()) return fields;

        JsonNode fieldsNode = typeNode.path("fields");
        if (fieldsNode.isArray()) {
            for (JsonNode field : fieldsNode) {
                Map<String, String> f = new HashMap<>();
                f.put("name", field.path("name").asText());
                f.put("description", field.path("description").asText());
                fields.add(f);
            }
        }
        return fields;
    }
}
