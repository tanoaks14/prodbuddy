package com.prodbuddy.tools.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodbuddy.core.system.QueryService;
import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GraphQL tool implementation. */
public final class GraphQLTool implements Tool {

    /** Tool name. */
    private static final String NAME = "graphql";
    /** Max preview length. */
    private static final int MAX_PREVIEW = 200;
    /** GraphQL client. */
    private final GraphQLClient client;
    /** JSON mapper. */
    private final ObjectMapper mapper;
    /** Schema service. */
    private final SchemaService schemaService;
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
    protected GraphQLTool(final GraphQLClient gqlClient,
                          final QueryService qs) {
        this.client = gqlClient;
        this.queryService = qs;
        this.mapper = new ObjectMapper();
        this.seqLog = com.prodbuddy.observation.ObservationContext.getLogger();
        this.schemaService = new SchemaService(gqlClient, mapper,
                new IntrospectionQueryBuilder(qs), seqLog, styling());
    }

    protected GraphQLTool(final GraphQLClient gqlClient) {
        this(gqlClient, new QueryService());
    }

    @Override
    public com.prodbuddy.core.tool.ToolStyling styling() {
        return new com.prodbuddy.core.tool.ToolStyling("#E10098", "#FFFFFF",
                "#FCE4EC", "🕸️ GraphQL", java.util.Map.of());
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
            NAME,
            "Generic GraphQL tool for querying and schema exploration",
            Set.of("graphql.query", "graphql.introspect",
                    "graphql.list_operations", "graphql.format")
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
        if (url.isBlank() && !"format".equals(request.operation())) {
            return ToolResponse.failure("MISSING_URL", "URL is required");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) request.payload()
                .get("auth");

        com.prodbuddy.observation.ObservationContext.log("Orchestrator",
                "GraphQL", request.operation(), "requested",
                styling().toMetadata("GraphQL"));

        return switch (request.operation()) {
            case "query" -> handleQuery(request, url, auth);
            case "format" -> handleFormat(request);
            case "introspect" -> schemaService.handleIntrospect(url, auth);
            case "list_operations" -> schemaService.handleListOperations(url, auth);
            default -> ToolResponse.failure("UNSUPPORTED_OP",
                    "Operation not supported: " + request.operation());
        };
    }

    private ToolResponse handleFormat(final ToolRequest request) {
        String query = resolveQuery(request.payload().get("query"));
        Map<String, Object> variables = null;
        try {
            variables = resolveVariables(request.payload().get("variables"));
        } catch (Exception e) {
            // Variables optional for format
        }
        boolean validate = Boolean.parseBoolean(String.valueOf(
                request.payload().getOrDefault("validate", "false")));
        if (validate) {
            String error = validateQuery(query, variables);
            if (error != null) {
                return ToolResponse.failure("GQL_VALIDATION_ERROR", error);
            }
        }
        return ToolResponse.ok(Map.of("formatted", query, "valid", true));
    }

    private ToolResponse handleQuery(final ToolRequest request,
                                     final String url,
                                     final Map<String, Object> auth) {
        String query = resolveQuery(request.payload().get("query"));
        Map<String, Object> variables;
        try {
            variables = resolveVariables(request.payload().get("variables"));
        } catch (Exception e) {
            return ToolResponse.failure("GQL_VARS_ERROR", e.getMessage());
        }

        boolean val = Boolean.parseBoolean(String.valueOf(
                request.payload().getOrDefault("validate", "false")));
        if (val) {
            String error = validateQuery(query, variables);
            if (error != null) {
                return ToolResponse.failure("GQL_VALIDATION_ERROR", error);
            }
        }

        return executeRequest(url, query, variables, auth, request);
    }

    private ToolResponse executeRequest(final String url,
                                        final String query,
                                        final Map<String, Object> variables,
                                        final Map<String, Object> auth,
                                        final ToolRequest request) {
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

    private String validateQuery(final String query,
                                 final Map<String, Object> variables) {
        if (query == null || query.isBlank()) {
            return "Query is empty";
        }
        String error = checkPlaceholders(query);
        if (error != null) return error;

        int braces = 0;
        for (char c : query.toCharArray()) {
            if (c == '{') braces++;
            else if (c == '}') braces--;
        }
        if (braces != 0) return "Unbalanced braces in query";
        return checkVariableConsistency(query, variables);
    }

    private String checkPlaceholders(final String query) {
        if (query.contains("${")) {
            int start = query.indexOf("${");
            int end = query.indexOf("}", start);
            String v = (end > start) ? query.substring(start, end + 1)
                    : "${...}";
            return "Unresolved placeholder found: " + v;
        }
        return null;
    }

    private String checkVariableConsistency(final String query,
                                            final Map<String, Object> vars) {
        if (vars != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "\\$([a-zA-Z0-9_]+)").matcher(query);
            while (m.find()) {
                String varName = m.group(1);
                if (!vars.containsKey(varName)) {
                    return "Query variable $" + varName + " is missing";
                }
            }
        }
        return null;
    }

    private String resolveQuery(final Object raw) {
        if (raw == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object line : list) {
                lines.add(String.valueOf(line).trim());
            }
        } else {
            for (String line : String.valueOf(raw).split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        }
        return String.join("\n", lines);
    }

    private Map<String, Object> resolveVariables(final Object raw)
            throws Exception {
        if (raw instanceof Map) return (Map<String, Object>) raw;
        if (raw instanceof String s && !s.isBlank()) {
            return mapper.readValue(s, Map.class);
        }
        return null;
    }

    private ToolResponse processResponse(final ToolRequest req,
                                         final String response)
            throws Exception {
        boolean noTrunc = Boolean.parseBoolean(String.valueOf(
                req.payload().getOrDefault("noTruncate", "false")));
        String p = String.valueOf(req.payload().getOrDefault(
                "maxOutputChars", "20000"));
        int max = noTrunc ? Integer.MAX_VALUE : Integer.parseInt(p);
        String finalRes = (response != null && response.length() > max)
                ? response.substring(0, max) : response;
        return ToolResponse.ok(Map.of("data", mapper.readTree(finalRes),
                "truncated", response != null && response.length() > max));
    }

    private void logQuery(final String url, final String query) {
        Map<String, String> meta = new java.util.HashMap<>(
                styling().toMetadata("GraphQL"));
        meta.put("style", "query");
        String preview = (query.length() > MAX_PREVIEW)
                ? query.substring(0, MAX_PREVIEW) + "..." : query;
        meta.put("noteText", "Query:\n" + preview);
        seqLog.logSequence("Orchestrator", "GraphQL", "QUERY", url, meta);
    }

    private void logResponse(final String msg, final String style) {
        Map<String, String> meta = new java.util.HashMap<>(
                styling().toMetadata("GraphQL"));
        meta.put("style", style);
        seqLog.logSequence("GraphQL", "Orchestrator",
                style.toUpperCase(), msg, meta);
    }
}
