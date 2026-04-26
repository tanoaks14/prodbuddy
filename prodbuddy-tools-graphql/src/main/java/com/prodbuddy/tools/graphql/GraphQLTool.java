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
    /** Sequence logger. */
    private final com.prodbuddy.observation.SequenceLogger seqLog;

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
        this.seqLog = com.prodbuddy.observation.ObservationContext.getLogger();
    }
    
    protected GraphQLTool(final GraphQLClient gqlClient) {
        this(gqlClient, new QueryService());
    }

    @Override
    public com.prodbuddy.core.tool.ToolStyling styling() {
        return new com.prodbuddy.core.tool.ToolStyling("#E10098", "#FFFFFF", "#FCE4EC", "🕸️ GraphQL", java.util.Map.of());
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
        String query = String.valueOf(request.payload().getOrDefault("query", ""));
        Map<String, Object> variables;
        try {
            variables = resolveVariables(request.payload().get("variables"));
        } catch (Exception e) {
            return ToolResponse.failure("GQL_VARS_ERROR", "Invalid variables JSON: " + e.getMessage());
        }

        try {
            logQuery(url, query);
            String response = client.execute(url, query, variables, auth);
            logResponse("OK", "success");
            return processResponse(request, response);
        } catch (Exception e) {
            logResponse(e.getMessage(), "error");
            return ToolResponse.failure("GQL_QUERY_ERROR", e.getMessage());
        }
    }

    private Map<String, Object> resolveVariables(Object raw) throws Exception {
        if (raw instanceof Map) return (Map<String, Object>) raw;
        if (raw instanceof String s && !s.isBlank()) return mapper.readValue(s, Map.class);
        return null;
    }

    private ToolResponse processResponse(ToolRequest req, String response) throws Exception {
        boolean noTrunc = Boolean.parseBoolean(String.valueOf(req.payload().getOrDefault("noTruncate", "false")));
        int max = noTrunc ? Integer.MAX_VALUE : Integer.parseInt(String.valueOf(req.payload().getOrDefault("maxOutputChars", "20000")));
        String finalRes = (response != null && response.length() > max) ? response.substring(0, max) : response;
        return ToolResponse.ok(Map.of("data", mapper.readTree(finalRes), "truncated", response != null && response.length() > max));
    }

    private void logQuery(String url, String query) {
        Map<String, String> meta = new java.util.HashMap<>(styling().toMetadata("GraphQL"));
        meta.put("style", "query");
        meta.put("noteText", "Query:\n" + (query.length() > 200 ? query.substring(0, 200) + "..." : query));
        seqLog.logSequence("Orchestrator", "GraphQL", "QUERY", url, meta);
    }

    private void logResponse(String msg, String style) {
        Map<String, String> meta = new java.util.HashMap<>(styling().toMetadata("GraphQL"));
        meta.put("style", style);
        seqLog.logSequence("GraphQL", "Orchestrator", style.toUpperCase(), msg, meta);
    }

    private ToolResponse handleIntrospect(final String url,
                                          final Map<String, Object> auth) {
        try {
            Map<String, String> qMeta = new java.util.HashMap<>(styling().toMetadata("GraphQL"));
            qMeta.put("style", "query");
            qMeta.put("noteText", "Introspection Query");
            seqLog.logSequence("Orchestrator", "GraphQL", "INTROSPECT", url, qMeta);

            String response = client.execute(url,
                    introspectionBuilder.getFullIntrospectionQuery(),
                    null, auth);

            seqLog.logSequence("GraphQL", "Orchestrator", "RESPONSE", "Schema Found", styling().toMetadata("GraphQL"));
            return ToolResponse.ok(Map.of("schema", mapper.readTree(response)));
        } catch (Exception e) {
            return ToolResponse.failure("GQL_INTROSPECT_ERROR", e.getMessage());
        }
    }

    ToolResponse handleListOperations(final String url,
                                      final Map<String, Object> auth) {
        try {
            Map<String, String> qMeta = new java.util.HashMap<>(styling().toMetadata("GraphQL"));
            qMeta.put("style", "query");
            qMeta.put("noteText", "Schema Operations Summary");
            seqLog.logSequence("Orchestrator", "GraphQL", "LIST_OPS", url, qMeta);

            String response = client.execute(url,
                    introspectionBuilder.getOperationsSummaryQuery(),
                    null, auth);
            JsonNode root = mapper.readTree(response);
            JsonNode schema = root.path("data").path("__schema");

            Map<String, Object> result = new HashMap<>();
            result.put("queries", extractFields(schema.path("queryType")));
            result.put("mutations", extractFields(schema.path("mutationType")));

            seqLog.logSequence("GraphQL", "Orchestrator", "RESPONSE", "Operations Mapped", styling().toMetadata("GraphQL"));
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
