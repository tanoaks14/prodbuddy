package com.prodbuddy.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.orchestrator.AgentLoopOrchestrator;
import com.prodbuddy.orchestrator.LoopConfig;
import com.prodbuddy.orchestrator.RuleBasedToolRouter;

public final class ProdBuddyApplication {

    public static void main(String[] args) {
        ToolBootstrap bootstrap = new ToolBootstrap();
        ToolRegistry registry = bootstrap.createRegistry();
        AgentLoopOrchestrator orchestrator = new AgentLoopOrchestrator(
                registry, new RuleBasedToolRouter(), LoopConfig.defaults()
        );
        Map<String, String> environment = loadEnvironment();
        AgentConfig agentConfig = AgentConfig.from(environment);
        if (isRecipeMode(args)) {
            RecipeCliHandler.handle(args, orchestrator, environment, agentConfig);
            return;
        }
        if (isDebugIssueMode(args)) {
            System.out.println("Agent provider: " + agentConfig.summary());
            runDebugIssueLoop(args, orchestrator, environment, agentConfig);
            return;
        }
        runDefaultMode(args, orchestrator, environment, agentConfig, registry);
    }

    private static void runDefaultMode(
            String[] args,
            AgentLoopOrchestrator orchestrator,
            Map<String, String> environment,
            AgentConfig agentConfig,
            ToolRegistry registry
    ) {
        System.out.println("Agent provider: " + agentConfig.summary());
        System.out.println("Registered tools: " + registry.metadata());
        runLocalLlmIfEnabled(args, agentConfig, registry.metadata());
        ToolRequest request = new ToolRequest("pdf", "read", Map.of("path", "sample.pdf"));
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), environment);
        System.out.println(orchestrator.run(request, context));
    }

    private static void runLocalLlmIfEnabled(String[] args, AgentConfig config, List<ToolMetadata> tools) {
        if (!config.enabled() || !"ollama".equalsIgnoreCase(config.provider())) {
            return;
        }
        String userPrompt = args.length > 0 ? String.join(" ", args) : "Summarize available tools in one paragraph.";
        String prompt = buildToolAwarePrompt(userPrompt, tools);
        String response = new OllamaAgentClient().generate(prompt, config);
        System.out.println("Local LLM response: " + response);
    }

    private static String buildToolAwarePrompt(String userPrompt, List<ToolMetadata> tools) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are assisting with ProdBuddy runtime health. Use only the tool inventory below.\n");
        builder.append("Tool inventory:\n");
        for (ToolMetadata tool : tools) {
            builder.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description())
                    .append(" capabilities=")
                    .append(tool.capabilities())
                    .append("\n");
        }
        builder.append("User request: ").append(userPrompt);
        return builder.toString();
    }

    private static boolean isDebugIssueMode(String[] args) {
        return args.length > 1 && "--debug-issue".equalsIgnoreCase(args[0]);
    }

    private static boolean isRecipeMode(String[] args) {
        if (args.length < 1) {
            return false;
        }
        String cmd = args[0].toLowerCase();
        return cmd.equals("--run-recipe")
                || cmd.equals("--list-recipes")
                || cmd.equals("--add-recipe");
    }

    private static void runDebugIssueLoop(
            String[] args,
            AgentLoopOrchestrator orchestrator,
            Map<String, String> environment,
            AgentConfig agentConfig
    ) {
        String issue = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        promptForMissingDebugConfig(environment);
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), environment);
        DebugIssueAgentLoop loop = new DebugIssueAgentLoop(orchestrator::run);
        ToolResponse report = loop.run(issue, context);
        System.out.println("Debug issue summary: " + summarizeReport(report));
        runDebugReportLlm(issue, report, agentConfig);
    }

    private static void runDebugReportLlm(String issue, ToolResponse report, AgentConfig config) {
        if (!config.enabled() || !"ollama".equalsIgnoreCase(config.provider())) {
            return;
        }
        String reportSummary = String.valueOf(summarizeReport(report)).replace('"', '\'');
        String prompt = "Issue: " + issue + "\nDebug report: " + reportSummary
                + "\nProvide likely root cause, confidence, and next 3 checks.";
        String response = new OllamaAgentClient().generate(prompt, config);
        System.out.println("Debug assistant response: " + response);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summarizeReport(ToolResponse report) {
        Map<String, Object> data = report.data();
        List<Map<String, Object>> failures = (List<Map<String, Object>>) data.getOrDefault("failures", List.of());
        List<Map<String, Object>> compactFailures = new ArrayList<>();
        for (Map<String, Object> failure : failures) {
            compactFailures.add(Map.of(
                    "step", failure.getOrDefault("step", "unknown"),
                    "errors", failure.getOrDefault("errors", List.of())
            ));
        }
        return Map.of(
                "issue", data.getOrDefault("issue", ""),
                "status", data.getOrDefault("status", "unknown"),
                "successfulSteps", data.getOrDefault("successfulSteps", 0),
                "failedSteps", data.getOrDefault("failedSteps", 0),
                "failures", compactFailures,
                "nextActions", data.getOrDefault("nextActions", List.of())
        );
    }

    private static void promptForMissingDebugConfig(Map<String, String> environment) {
        promptIfMissing(environment, "NEWRELIC_ACCOUNT_ID", false);
        promptIfMissing(environment, "NEWRELIC_USER_API_KEY", true);
        promptIfMissing(environment, "SPLUNK_BASE_URL", false);
        promptIfMissing(environment, "SPLUNK_AUTH_MODE", false);
        String splunkAuthMode = environment.getOrDefault("SPLUNK_AUTH_MODE", "token").trim().toLowerCase();
        if ("user".equals(splunkAuthMode)) {
            promptIfMissing(environment, "SPLUNK_USERNAME", false);
            promptIfMissing(environment, "SPLUNK_PASSWORD", true);
        } else {
            promptIfMissing(environment, "SPLUNK_TOKEN", true);
        }
        promptIfMissing(environment, "ELASTICSEARCH_BASE_URL", false);
    }

    private static void promptIfMissing(Map<String, String> environment, String key, boolean secret) {
        String existing = environment.get(key);
        if (existing != null && !existing.isBlank()) {
            return;
        }
        String prompt = "Missing " + key + ". Enter value (or press Enter to skip): ";
        String input = readInput(prompt, secret);
        if (input != null && !input.isBlank()) {
            environment.put(key, input.trim());
        }
    }

    private static String readInput(String prompt, boolean secret) {
        return secret ? ConsoleInput.readSecret(prompt) : ConsoleInput.readLine(prompt);
    }

    private static Map<String, String> loadEnvironment() {
        Map<String, String> values = new LinkedHashMap<>(new EnvFileLoader().load(Path.of(".env")));
        values.putAll(System.getenv());
        return values;
    }
}