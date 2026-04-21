package com.prodbuddy.app;

import com.prodbuddy.context.ContextCollector;
import com.prodbuddy.context.ContextFormatter;
import com.prodbuddy.context.ConversationContext;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.orchestrator.AgentLoopOrchestrator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DebugIssueHandler {

    private DebugIssueHandler() {}

    public static void handle(
            String[] args,
            AgentLoopOrchestrator orchestrator,
            Map<String, String> environment,
            com.prodbuddy.core.agent.AgentConfig agentConfig
    ) {
        String issue = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (issue.isBlank()) {
            System.out.println("Usage: --debug-issue <issue summary>");
            return;
        }
        promptForMissingDebugConfig(environment);
        promptForMissingCodeContextConfig(environment);
        printDebugContext(issue, environment);
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), environment);
        ConversationContext convCtx = new ConversationContext(context.requestId());
        ContextCollector collector = new ContextCollector(orchestrator::run, convCtx);
        DebugIssueAgentLoop loop = new DebugIssueAgentLoop(collector::execute, new CliDebugProgressListener());
        ToolResponse report = loop.run(issue, context);
        printDebugSummary(report);
        
        String contextFilePath = "debug-issue-context.md";
        writeContextFile(contextFilePath, ContextFormatter.format(convCtx));
        runDebugReportLlm(issue, report, agentConfig, environment, contextFilePath);
    }

    private static void promptForMissingDebugConfig(Map<String, String> environment) {
        promptIfMissing(environment, "NEWRELIC_ACCOUNT_ID", false);
        promptIfMissing(environment, "NEWRELIC_USER_API_KEY", true);
        promptIfMissing(environment, "SPLUNK_BASE_URL", false);
        SplunkDebugConfigPrompter.prompt(environment);
        promptIfMissing(environment, "ELASTICSEARCH_BASE_URL", false);
    }

    private static void promptForMissingCodeContextConfig(Map<String, String> environment) {
        String cwd = System.getProperty("user.dir");
        String selectedProject = promptWithDefault("Project path for code-based analysis",
                environment.getOrDefault("PRODBUDDY_PROJECT_PATH", cwd));
        environment.put("PRODBUDDY_PROJECT_PATH", selectedProject);
        String selectedDbPath = promptWithDefault("Code-context DB path",
                environment.getOrDefault("CODE_CONTEXT_DB_PATH", ".prodbuddy/codegraph"));
        environment.put("CODE_CONTEXT_DB_PATH", selectedDbPath);
        String codeHint = promptWithDefault("Optional source location hint (module/path/class) for better tool targeting",
                environment.getOrDefault("PRODBUDDY_CODE_HINT", ""));
        if (!codeHint.isBlank()) {
            environment.put("PRODBUDDY_CODE_HINT", codeHint);
        }
    }

    private static String promptWithDefault(String label, String defaultValue) {
        String value = defaultValue == null ? "" : defaultValue.trim();
        String prompt = label + (value.isBlank() ? ": " : " [" + value + "]: ");
        String input = readInput(prompt, false);
        if (input == null || input.isBlank()) {
            return value;
        }
        return input.trim();
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

    private static void printDebugContext(String issue, Map<String, String> environment) {
        System.out.println("\n=== Debug Inputs ===");
        System.out.println("Issue: " + issue);
        System.out.println("Project path: " + environment.getOrDefault("PRODBUDDY_PROJECT_PATH", ""));
        System.out.println("Code DB path: " + environment.getOrDefault("CODE_CONTEXT_DB_PATH", ""));
        System.out.println("Splunk auth mode: " + environment.getOrDefault("SPLUNK_AUTH_MODE", "token"));
        System.out.println("Splunk index: " + environment.getOrDefault("SPLUNK_DEFAULT_INDEX", ""));
    }

    @SuppressWarnings("unchecked")
    private static void printDebugSummary(ToolResponse report) {
        System.out.println("\n=== Debug Summary ===");
        Map<String, Object> data = summarizeReport(report);
        System.out.println("Status: " + data.getOrDefault("status", "unknown"));
        System.out.println("Successful steps: " + data.getOrDefault("successfulSteps", 0));
        System.out.println("Failed steps: " + data.getOrDefault("failedSteps", 0));

        List<Map<String, Object>> failures = (List<Map<String, Object>>) data.getOrDefault("failures", List.of());
        if (!failures.isEmpty()) {
            System.out.println("Failures:");
            for (Map<String, Object> failure : failures) {
                System.out.println("- " + failure.getOrDefault("step", "unknown") + ": " + failure.getOrDefault("errors", ""));
            }
        }
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
                "status", data.getOrDefault("status", "unknown"),
                "successfulSteps", data.getOrDefault("successfulSteps", 0),
                "failedSteps", data.getOrDefault("failedSteps", 0),
                "failures", compactFailures
        );
    }

    private static void runDebugReportLlm(String issue, ToolResponse report, com.prodbuddy.core.agent.AgentConfig config, Map<String, String> environment, String path) {
        if (!config.enabled() || !"ollama".equalsIgnoreCase(config.provider())) return;
        String prompt = "Issue: " + issue + "\nDebug report: " + summarizeReport(report) + "\nContext file: " + path;
        String response = new com.prodbuddy.core.agent.OllamaAgentClient().generate(prompt, config);
        System.out.println("\n=== Debug Assistant ===\n" + TerminalMarkdownRenderer.toTerminalText(response));
    }

    private static void writeContextFile(String path, String data) {
        try {
            Files.writeString(Path.of(path), data);
            System.out.println("Detailed execution context saved to: " + path);
        } catch (IOException e) {
            System.err.println("Failed to write context file: " + e.getMessage());
        }
    }
}
