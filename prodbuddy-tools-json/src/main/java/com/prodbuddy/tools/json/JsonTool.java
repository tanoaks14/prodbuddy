package com.prodbuddy.tools.json;

import com.prodbuddy.core.tool.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JsonTool implements Tool {

    private static final String NAME = "json";
    private final JsonAnalyzer analyzer;
    private final com.prodbuddy.observation.SequenceLogger seqLog;

    public JsonTool(JsonAnalyzer analyzer) {
        this.analyzer = analyzer;
        this.seqLog = com.prodbuddy.observation.ObservationContext.getLogger();
    }

    @Override
    public com.prodbuddy.core.tool.ToolStyling styling() {
        return new com.prodbuddy.core.tool.ToolStyling("#FFF9C4", "#FBC02D", "#FFFDE7", "📄 JSON", java.util.Map.of());
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(NAME, "JSON analysis tool", Set.of("json.assert", "json.search", "json.extract"));
    }

    @Override
    public boolean supports(ToolRequest request) {
        return NAME.equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) throws ToolExecutionException {
        seqLog.logSequence("AgentLoopOrchestrator", NAME, "execute", "Evaluating JSON payload", 
                styling().toMetadata());
        String data = stripMarkdown(String.valueOf(request.payload().getOrDefault("data", "")));
        if (data.isBlank() || data.equals("null")) {
            return ToolResponse.failure("JSON_BAD_REQUEST", "data is required");
        }

        if ("assert".equalsIgnoreCase(request.operation())) {
            return runAssert(data, request.payload());
        }

        if ("search".equalsIgnoreCase(request.operation())) {
            return runSearch(data, request.payload());
        }

        if ("extract".equalsIgnoreCase(request.operation())) {
            return runExtract(data, request.payload());
        }

        return ToolResponse.failure("JSON_UNSUPPORTED_OP", "supported ops: assert, search");
    }

    private ToolResponse runAssert(String data, Map<String, Object> req) {
        String path = String.valueOf(req.getOrDefault("path", ""));
        String expected = String.valueOf(req.getOrDefault("expected", ""));
        seqLog.logSequence(NAME, "JsonAnalyzer", "assertPathEq", "Asserting path: " + path,
                styling().toMetadata());
        boolean matched = analyzer.assertPathEq(data, path, expected);
        if (matched) {
            return ToolResponse.ok(Map.of("matched", true, "path", path));
        }
        return ToolResponse.failure("JSON_ASSERT_FAILED", "Value at " + path + " did not match " + expected);
    }

    private ToolResponse runSearch(String data, Map<String, Object> req) {
        String key = String.valueOf(req.getOrDefault("key", ""));
        seqLog.logSequence(NAME, "JsonAnalyzer", "searchKey", "Searching key: " + key,
                styling().toMetadata());
        List<String> paths = analyzer.searchKey(data, key);
        return ToolResponse.ok(Map.of(
            "key", key,
            "count", paths.size(),
            "paths", paths
        ));
    }

    private ToolResponse runExtract(final String data, final Map<String, Object> req) {
        Object pathsObj = req.get("paths");
        Object regexObj = req.get("regex");
        com.prodbuddy.observation.ObservationContext.log(NAME, "JsonAnalyzer", "extract", "Extracting values",
                styling().toMetadata());

        Map<String, Object> results = new java.util.HashMap<>();
        Map<String, List<String>> traces = new java.util.HashMap<>();

        if (pathsObj instanceof Map<?, ?> paths) {
            processPaths(data, paths, results, traces);
        }
        if (regexObj instanceof Map<?, ?> regex) {
            // Future regex extraction logic
        }

        results.put("debug_traces", traces);
        return ToolResponse.ok(results);
    }

    private void processPaths(String data, Map<?, ?> paths, Map<String, Object> results, Map<String, List<String>> traces) {
        for (Map.Entry<?, ?> entry : paths.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String path = String.valueOf(entry.getValue());
            seqLog.logSequence(NAME, "JsonAnalyzer", "extractPart", "Key: " + key + ", Path: " + path,
                    styling().toMetadata());
            JsonAnalyzer.TraceResult tr = analyzer.walkWithTrace(data, path);
            traces.put(key + "_trace", tr.trace());
            if (tr.node() != null && !tr.node().isMissingNode()) {
                results.put(key, tr.node().isContainerNode() ? tr.node().toString() : tr.node().asText());
            }
        }
    }
    
    private String stripMarkdown(String s) {
        if (s == null || !s.contains("```")) return s;
        String[] parts = s.split("```");
        for (String part : parts) {
            String trimmed = part.trim();
            // Look for first chunk that looks like JSON after the code block marker
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed;
            if (trimmed.startsWith("json")) {
                String nested = trimmed.substring(4).trim();
                if (nested.startsWith("{") || nested.startsWith("[")) return nested;
            }
        }
        return s;
    }
}
