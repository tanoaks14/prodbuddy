package com.prodbuddy.tools.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodbuddy.core.system.QueryService;
import com.prodbuddy.core.tool.*;

import java.util.*;

/** GraphQL tool implementation. */
public final class GraphQLTool implements Tool {

    /** Tool name. */
    private static final String NAME = "graphql";
    /** GraphQL client. */
    private final GraphQLClient client;
    /** JSON mapper. */
    private final ObjectMapper mapper;
    /** Introspection builder. */
    private final IntrospectionQueryBuilder introspectionBuilder;
    /** Query service. */
    private final QueryService queryService;

    /** Constructor. */
    public GraphQLTool() {
        this(new GraphQLClient(), new QueryService());
    }

    /**
     * Protected constructor for testing.
     * @param gqlClient GraphQL client.
     * @param qs Query service.
     */
    protected GraphQLTool(final GraphQLClient gqlClient, final QueryService qs) {
        this.client = gqlClient;
        this.queryService = qs;
        this.introspectionBuilder = new IntrospectionQueryBuilder(qs);
        this.mapper = new ObjectMapper();
    }
    
    protected GraphQLTool(final GraphQLClient gqlClient) {
        this(gqlClient, new QueryService());
    }

    @Override
    public com.prodbuddy.core.tool.ToolStyling styling() {
        return new com.prodbuddy.core.tool.ToolStyling("#FCE4EC", "#880E4F", "#F8BBD0", "🕸️ GraphQL", java.util.Map.of());
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
            NAME,
            "Generic GraphQL tool for querying and schema exploration",
            Set.of("graphql.query", "graphql.introspect",
                    "graphql.list_operations")
        );
    }

    @Override
    public boolean supports(final ToolRequest request) {
        return NAME.equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(final ToolRequest request,
                                final ToolContext context) {
        String url = String.valueOf(request.payload().getOrDefault("url", ""));
        if (url.isBlank()) {
            return ToolResponse.failure("MISSING_URL",
                    "GraphQL endpoint URL is required");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) request.payload()
                .get("auth");

        com.prodbuddy.observation.ObservationContext.log("Orchestrator", "GraphQL",
                request.operation(), "requested", styling().toMetadata("GraphQL"));

        return switch (request.operation()) {
            case "query" -> handleQuery(request, url, auth);
            case "introspect" -> handleIntrospect(url, auth);
            case "list_operations" -> handleListOperations(url, auth);
            default -> ToolResponse.failure("UNSUPPORTED_OP",
                    "Operation not supported: " + request.operation());
        };
    }

    private ToolResponse handleQuery(final ToolRequest request,
                                     final String url,
                                     final Map<String, Object> auth) {
        String query = String.valueOf(request.payload()
                .getOrDefault("query", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) request.payload()
                .get("variables");

        try {
            String response = client.execute(url, query, variables, auth);

            final boolean noTruncate = Boolean.parseBoolean(String.valueOf(
                    request.payload().getOrDefault("noTruncate", "false")));
            final int maxChars = noTruncate ? Integer.MAX_VALUE : Integer
                    .parseInt(String.valueOf(request.payload().getOrDefault(
                            "maxOutputChars", "20000")));

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

    private ToolResponse handleIntrospect(final String url,
                                          final Map<String, Object> auth) {
        try {
            String response = client.execute(url,
                    introspectionBuilder.getFullIntrospectionQuery(),
                    null, auth);
            return ToolResponse.ok(Map.of("schema", mapper.readTree(response)));
        } catch (Exception e) {
            return ToolResponse.failure("GQL_INTROSPECT_ERROR", e.getMessage());
        }
    }

    ToolResponse handleListOperations(final String url,
                                      final Map<String, Object> auth) {
        try {
            String response = client.execute(url,
                    introspectionBuilder.getOperationsSummaryQuery(),
                    null, auth);
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

    List<Map<String, String>> extractFields(final JsonNode typeNode) {
        List<Map<String, String>> fields = new ArrayList<>();
        if (typeNode.isMissingNode()) {
            return fields;
        }

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
