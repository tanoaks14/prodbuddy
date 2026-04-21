package com.prodbuddy.core.system;

import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Supplier;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.core.tool.ToolRouter;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

public final class SystemCatalogTool implements Tool {

    private static final String NAME = "system";
    private final Supplier<ToolRegistry> registrySupplier;
    private final ToolRouter router;
    private final SequenceLogger seqLog;

    public SystemCatalogTool(Supplier<ToolRegistry> registrySupplier, ToolRouter router) {
        this.registrySupplier = registrySupplier;
        this.router = router;
        this.seqLog = new Slf4jSequenceLogger(SystemCatalogTool.class);
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                NAME,
                "System catalog and agent config tool",
                Set.of("system.list_tools", "system.tool_details", "system.agent_config", "system.tool_compatibility", "system.ask")
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        String intent = request.intent().toLowerCase();
        return intent.equals("system") || intent.equals("catalog") || intent.equals("discovery");
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        seqLog.logSequence("AgentLoopOrchestrator", "system", "execute", "System " + request.operation());
        return switch (request.operation().toLowerCase()) {
            case "list_tools" ->
                listTools();
            case "tool_details" ->
                toolDetails(request.payload());
            case "agent_config" ->
                agentConfig(context);
            case "tool_compatibility" ->
                compatibility(request.payload());
            case "ask" ->
                ask(request.payload());
            default ->
                ToolResponse.failure("SYSTEM_OPERATION", "supported operations: list_tools, tool_details, agent_config, tool_compatibility, ask");
        };
    }

    private ToolResponse listTools() {
        List<ToolInfo> tools = registrySupplier.get().metadata().stream().map(ToolInfo::from).toList();
        return ToolResponse.ok(Map.of("tools", tools));
    }

    private ToolResponse toolDetails(Map<String, Object> payload) {
        String toolName = String.valueOf(payload.getOrDefault("name", ""));
        return registrySupplier.get().find(toolName)
                .map(tool -> ToolResponse.ok(Map.of(
                "name", tool.metadata().name(),
                "description", tool.metadata().description(),
                "capabilities", tool.metadata().capabilities(),
                "requiredEnv", requiredEnv(toolName)
        )))
                .orElseGet(() -> ToolResponse.failure("SYSTEM_TOOL", "tool not found: " + toolName));
    }

    private ToolResponse agentConfig(ToolContext context) {
        AgentConfigInfo info = new AgentConfigInfo(
                context.envOrDefault("AGENT_PROVIDER", "ollama"),
                context.envOrDefault("AGENT_MODEL", "gemma4:e4b"),
                context.envOrDefault("AGENT_BASE_URL", "http://localhost:11434"),
                Boolean.parseBoolean(context.envOrDefault("AGENT_ENABLED", "false")),
                Boolean.parseBoolean(context.envOrDefault("AGENT_AUTH_ENABLED", "false"))
        );
        return ToolResponse.ok(Map.of("agent", info));
    }

    private ToolResponse compatibility(Map<String, Object> payload) {
        String intent = String.valueOf(payload.getOrDefault("intent", ""));
        return ToolResponse.ok(Map.of("intent", intent, "routedTool", router.route(new ToolRequest(intent, "query", Map.of()))));
    }

    private ToolResponse ask(Map<String, Object> payload) {
        String question = String.valueOf(payload.getOrDefault("question", "Please provide input:"));
        System.out.println("\n[PRODBUDDY INTERACTIVE] " + question);
        System.out.print("> ");
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextLine()) {
            String answer = scanner.nextLine();
            return ToolResponse.ok(Map.of("answer", answer));
        }
        return ToolResponse.failure("SYSTEM_ASK_FAILED", "No input received");
    }

    private List<String> requiredEnv(String toolName) {
        return switch (toolName) {
            case "newrelic" ->
                List.of("NEWRELIC_ACCOUNT_ID", "NEWRELIC_USER_API_KEY");
            case "elasticsearch" ->
                List.of("ELASTICSEARCH_BASE_URL", "ELASTICSEARCH_API_KEY");
            case "splunk" ->
                List.of("SPLUNK_BASE_URL", "SPLUNK_TOKEN");
            case "http" ->
                List.of("HTTP_DEFAULT_TIMEOUT_SECONDS", "HTTP_DEFAULT_AUTH_ENABLED");
            case "kubectl" ->
                List.of("KUBECTL_NAMESPACE", "KUBECTL_EXECUTE");
            case "codecontext" ->
                List.of("CODE_CONTEXT_MAX_RESULTS");
            default ->
                List.of();
        };
    }
}
