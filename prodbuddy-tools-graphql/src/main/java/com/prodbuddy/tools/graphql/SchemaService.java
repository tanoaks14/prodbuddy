package com.prodbuddy.tools.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Service for GraphQL schema operations. */
public final class SchemaService {

    private final GraphQLClient client;
    private final ObjectMapper mapper;
    private final IntrospectionQueryBuilder builder;
    private final SequenceLogger seqLog;
    private final com.prodbuddy.core.tool.ToolStyling styling;

    public SchemaService(final GraphQLClient client,
                         final ObjectMapper mapper,
                         final IntrospectionQueryBuilder builder,
                         final SequenceLogger seqLog,
                         final com.prodbuddy.core.tool.ToolStyling styling) {
        this.client = client;
        this.mapper = mapper;
        this.builder = builder;
        this.seqLog = seqLog;
        this.styling = styling;
    }

    /**
     * Handles introspection.
     * @param url URL.
     * @param auth Auth.
     * @return Response.
     */
    public ToolResponse handleIntrospect(final String url,
                                         final Map<String, Object> auth) {
        try {
            logSchemaOp("INTROSPECT", "Introspection Query", url);
            String response = client.execute(url,
                    builder.getFullIntrospectionQuery(), null, auth);
            seqLog.logSequence("GraphQL", "Orchestrator", "RESPONSE",
                    "Schema Found", styling.toMetadata("GraphQL"));
            return ToolResponse.ok(Map.of("schema", mapper.readTree(response)));
        } catch (Exception e) {
            return ToolResponse.failure("GQL_INTROSPECT_ERROR", e.getMessage());
        }
    }

    /**
     * Handles list operations.
     * @param url URL.
     * @param auth Auth.
     * @return Response.
     */
    public ToolResponse handleListOperations(final String url,
                                             final Map<String, Object> auth) {
        try {
            logSchemaOp("LIST_OPS", "Schema Operations Summary", url);
            String response = client.execute(url,
                    builder.getOperationsSummaryQuery(), null, auth);
            JsonNode schema = mapper.readTree(response).path("data").path("__schema");
            Map<String, Object> result = new HashMap<>();
            result.put("queries", extractFields(schema.path("queryType")));
            result.put("mutations", extractFields(schema.path("mutationType")));
            seqLog.logSequence("GraphQL", "Orchestrator", "RESPONSE",
                    "Operations Mapped", styling.toMetadata("GraphQL"));
            return ToolResponse.ok(result);
        } catch (Exception e) {
            return ToolResponse.failure("GQL_LIST_ERROR", e.getMessage());
        }
    }

    private void logSchemaOp(final String op, final String note, final String url) {
        Map<String, String> qMeta = new HashMap<>(styling.toMetadata("GraphQL"));
        qMeta.put("style", "query");
        qMeta.put("noteText", note);
        seqLog.logSequence("Orchestrator", "GraphQL", op, url, qMeta);
    }

    private List<Map<String, String>> extractFields(final JsonNode typeNode) {
        List<Map<String, String>> fields = new ArrayList<>();
        if (typeNode.isMissingNode()) return fields;
        JsonNode nodes = typeNode.path("fields");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                Map<String, String> f = new HashMap<>();
                f.put("name", node.path("name").asText());
                f.put("description", node.path("description").asText());
                fields.add(f);
            }
        }
        return fields;
    }
}
