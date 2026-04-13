package com.prodbuddy.tools.codecontext;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class JavaCodeContextToolTest {

    @Test
    void shouldReturnSummary() throws IOException {
        Path temp = Files.createTempDirectory("prodbuddy-code");
        Files.writeString(temp.resolve("Sample.java"), "class Sample {}\n");

        JavaCodeContextTool tool = new JavaCodeContextTool(
                new JavaProjectSummaryService(),
                new JavaCodeSearchService(),
                new JavaGraphExtractor(),
                new LocalGraphDbService()
        );
        ToolRequest request = new ToolRequest("code", "summary", Map.of("projectPath", temp.toString()));
        ToolResponse response = tool.execute(request, new ToolContext("1", Map.of()));

        Assertions.assertTrue(response.success());
        Assertions.assertEquals(Boolean.TRUE, response.data().get("exists"));
    }

    @Test
    void shouldBuildAndQueryGraphDb() throws IOException {
        Path temp = Files.createTempDirectory("prodbuddy-graph");
        Files.writeString(temp.resolve("Sample.java"), "class Sample { public void run() {} }\n");
        Path dbPath = temp.resolve("codegraph");

        JavaCodeContextTool tool = new JavaCodeContextTool(
                new JavaProjectSummaryService(),
                new JavaCodeSearchService(),
                new JavaGraphExtractor(),
                new LocalGraphDbService()
        );

        ToolRequest build = new ToolRequest(
                "codecontext",
                "build_graph_db",
                Map.of("projectPath", temp.toString(), "dbPath", dbPath.toString())
        );
        ToolResponse buildResponse = tool.execute(build, new ToolContext("1", Map.of()));
        Assertions.assertTrue(buildResponse.success());

        ToolRequest query = new ToolRequest(
                "codecontext",
                "query_graph",
                Map.of("dbPath", dbPath.toString(), "sql", "SELECT COUNT(*) AS cnt FROM ClassNode")
        );
        ToolResponse queryResponse = tool.execute(query, new ToolContext("2", Map.of()));
        Assertions.assertTrue(queryResponse.success());
    }

    @Test
    void shouldSkipRefreshWhenCodebaseUnchanged() throws IOException {
        Path temp = Files.createTempDirectory("prodbuddy-refresh");
        Files.writeString(temp.resolve("Sample.java"), "class Sample { int a = 1; }\n");
        Path dbPath = temp.resolve("codegraph-refresh");
        JavaCodeContextTool tool = newTool();

        ToolResponse firstResponse = tool.execute(
                new ToolRequest("codecontext", "refresh_graph_db", Map.of("projectPath", temp.toString(), "dbPath", dbPath.toString())),
                new ToolContext("3", Map.of())
        );
        ToolResponse secondResponse = tool.execute(
                new ToolRequest("codecontext", "refresh_graph_db", Map.of("projectPath", temp.toString(), "dbPath", dbPath.toString())),
                new ToolContext("4", Map.of())
        );

        Assertions.assertTrue(firstResponse.success());
        Assertions.assertEquals("rebuilt", firstResponse.data().get("status"));
        Assertions.assertTrue(secondResponse.success());
        Assertions.assertEquals("skipped", secondResponse.data().get("status"));
    }

    @Test
    void shouldSearchRealCodeSample() throws IOException {
        Path temp = createSmallRealProject();
        ToolResponse response = executeSearch(temp, "PAYMENT_TIMEOUT", "5", "5");

        Assertions.assertTrue(response.success());
        List<?> matches = (List<?>) response.data().get("matches");
        Assertions.assertFalse(matches.isEmpty());
        Assertions.assertTrue(containsMatch(matches, "PaymentService.java", "PAYMENT_TIMEOUT"));
    }

    @Test
    void shouldSearchCaseInsensitiveAcrossMultipleFiles() throws IOException {
        Path temp = createSmallRealProject();
        ToolResponse response = executeSearch(temp, "payment_timeout", "6", "10");

        List<?> matches = (List<?>) response.data().get("matches");
        Assertions.assertTrue(response.success());
        Assertions.assertTrue(matches.size() >= 2);
        Assertions.assertTrue(containsMatch(matches, "PaymentService.java", "PAYMENT_TIMEOUT"));
        Assertions.assertTrue(containsMatch(matches, "OrderController.java", "payment_timeout"));
    }

    @Test
    void shouldRespectMaxResultsLimitForSearch() throws IOException {
        Path temp = createSmallRealProject();
        ToolResponse response = executeSearch(temp, "payment", "7", "2");

        List<?> matches = (List<?>) response.data().get("matches");
        Assertions.assertTrue(response.success());
        Assertions.assertEquals(2, matches.size());
    }

    @Test
    void shouldIncludeLineNumbersAndDistinctFilesInSearchResults() throws IOException {
        Path temp = createSmallRealProject();
        ToolResponse response = executeSearch(temp, "payment", "8", "20");
        List<?> matches = (List<?>) response.data().get("matches");
        Set<String> files = new HashSet<>();

        for (Object item : matches) {
            Map<?, ?> match = (Map<?, ?>) item;
            files.add(match.get("file").toString());
            Assertions.assertTrue(((Number) match.get("line")).intValue() > 0);
            Assertions.assertFalse(match.get("snippet").toString().isBlank());
        }

        Assertions.assertTrue(files.stream().anyMatch(path -> path.endsWith("PaymentService.java")));
        Assertions.assertTrue(files.stream().anyMatch(path -> path.endsWith("OrderController.java")));
    }

    @Test
    void shouldReturnComprehensiveContextFromQuery() throws IOException {
        Path temp = createSmallRealProject();
        Path dbPath = temp.resolve("query-context-db");
        JavaCodeContextTool tool = newTool();

        ToolResponse buildResponse = tool.execute(
                new ToolRequest("codecontext", "build_graph_db", Map.of("projectPath", temp.toString(), "dbPath", dbPath.toString())),
                new ToolContext("9", Map.of())
        );
        Assertions.assertTrue(buildResponse.success());

        ToolResponse queryResponse = tool.execute(
                new ToolRequest("codecontext", "context_from_query", Map.of(
                        "projectPath", temp.toString(),
                        "dbPath", dbPath.toString(),
                        "query", "payment timeout exception in order flow"
                )),
                new ToolContext("10", Map.of("CODE_CONTEXT_MAX_RESULTS", "5"))
        );

        Assertions.assertTrue(queryResponse.success());
        Assertions.assertEquals("performance", ((Map<?, ?>) queryResponse.data().get("intent")).get("category"));
        Assertions.assertFalse(((List<?>) queryResponse.data().get("primaryMatches")).isEmpty());
        Assertions.assertFalse(((List<?>) queryResponse.data().get("rankedFindings")).isEmpty());
        Assertions.assertEquals(Boolean.TRUE, ((Map<?, ?>) queryResponse.data().get("graphContext")).get("available"));
        Assertions.assertFalse(((List<?>) queryResponse.data().get("nextActions")).isEmpty());
    }

    @Test
    void shouldRequireQueryForContextFromQuery() throws IOException {
        Path temp = createSmallRealProject();
        ToolResponse response = newTool().execute(
                new ToolRequest("codecontext", "context_from_query", Map.of("projectPath", temp.toString())),
                new ToolContext("11", Map.of())
        );

        Assertions.assertTrue(response.success());
        Assertions.assertEquals("query must be provided", response.data().get("error"));
    }

    @Test
    void shouldGenerateDeterministicIncidentReport() throws IOException {
        Path temp = createSmallRealProject();
        Path dbPath = temp.resolve("incident-db");
        JavaCodeContextTool tool = newTool();

        ToolResponse buildResponse = tool.execute(
                new ToolRequest("codecontext", "build_graph_db", Map.of("projectPath", temp.toString(), "dbPath", dbPath.toString())),
                new ToolContext("12", Map.of())
        );
        Assertions.assertTrue(buildResponse.success());

        ToolResponse report = tool.execute(
                new ToolRequest("codecontext", "incident_report", Map.of(
                        "projectPath", temp.toString(),
                        "dbPath", dbPath.toString(),
                        "query", "payment timeout 5xx"
                )),
                new ToolContext("13", Map.of("CODE_CONTEXT_MAX_RESULTS", "5"))
        );

        Assertions.assertTrue(report.success());
        Assertions.assertTrue(report.data().containsKey("telemetryQueries"));
        Assertions.assertTrue(report.data().containsKey("correlationRules"));
        Assertions.assertTrue(report.data().containsKey("recommendedExecutionOrder"));
    }

    private JavaCodeContextTool newTool() {
        return new JavaCodeContextTool(
                new JavaProjectSummaryService(),
                new JavaCodeSearchService(),
                new JavaGraphExtractor(),
                new LocalGraphDbService()
        );
    }

    private ToolResponse executeSearch(Path projectPath, String query, String requestId, String maxResults) {
        ToolRequest request = new ToolRequest(
                "codecontext",
                "search",
                Map.of("projectPath", projectPath.toString(), "query", query)
        );
        return newTool().execute(request, new ToolContext(requestId, Map.of("CODE_CONTEXT_MAX_RESULTS", maxResults)));
    }

    private boolean containsMatch(List<?> matches, String fileName, String snippetText) {
        return matches.stream()
                .map(item -> (Map<?, ?>) item)
                .anyMatch(item -> item.get("file").toString().endsWith(fileName)
                && item.get("snippet").toString().toLowerCase().contains(snippetText.toLowerCase()));
    }

    private Path createSmallRealProject() throws IOException {
        Path temp = Files.createTempDirectory("prodbuddy-real-search");
        Path sourceDir = temp.resolve("src/main/java/sample");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("PaymentService.java"), paymentServiceCode());
        Files.writeString(sourceDir.resolve("OrderController.java"), orderControllerCode());
        Files.writeString(sourceDir.resolve("HealthController.java"), healthControllerCode());
        return temp;
    }

    private String paymentServiceCode() {
        return "package sample;\n"
                + "class PaymentService {\n"
                + "  String pay(String id) {\n"
                + "    if (id == null) throw new IllegalStateException(\"PAYMENT_TIMEOUT\");\n"
                + "    return \"payment-ok\";\n"
                + "  }\n"
                + "}\n";
    }

    private String orderControllerCode() {
        return "package sample;\n"
                + "class OrderController {\n"
                + "  String createOrder(String id) {\n"
                + "    return \"payment_timeout for order \" + id;\n"
                + "  }\n"
                + "}\n";
    }

    private String healthControllerCode() {
        return "package sample;\n"
                + "class HealthController {\n"
                + "  String ping() { return \"UP\"; }\n"
                + "}\n";
    }
}
