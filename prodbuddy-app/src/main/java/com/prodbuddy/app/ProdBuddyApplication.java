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
        printSection("Runtime");
        System.out.println("Agent provider: " + agentConfig.summary());
        System.out.println("Registered tools: " + registry.metadata().size());
        runLocalLlmIfEnabled(args, agentConfig, registry.metadata());
        ToolRequest request = new ToolRequest("pdf", "read", Map.of("path", "sample.pdf"));
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), environment);
        printSection("Sample Run");
        System.out.println(orchestrator.run(request, context));
    }

    private static void runLocalLlmIfEnabled(String[] args, AgentConfig config, List<ToolMetadata> tools) {
        if (!config.enabled() || !"ollama".equalsIgnoreCase(config.provider())) {
            return;
        }
        String userPrompt = args.length > 0 ? String.join(" ", args) : "Summarize available tools in one paragraph.";
        String prompt = buildToolAwarePrompt(userPrompt, tools);
        String response = new OllamaAgentClient().generate(prompt, config);
        printSection("Local Assistant");
        System.out.println(TerminalMarkdownRenderer.toTerminalText(response));
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
        if (issue.isBlank()) {
            System.out.println("Usage: --debug-issue <issue summary>");
            return;
        }
        promptForMissingDebugConfig(environment);
        promptForMissingCodeContextConfig(environment);
        printDebugContext(issue, environment);
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), environment);
        DebugIssueAgentLoop loop = new DebugIssueAgentLoop(orchestrator::run, new CliDebugProgressListener());
        ToolResponse report = loop.run(issue, context);
        printDebugSummary(report);
        runDebugReportLlm(issue, report, agentConfig, environment);
    }

    private static void runDebugReportLlm(
            String issue,
            ToolResponse report,
            AgentConfig config,
            Map<String, String> environment
    ) {
        if (!config.enabled() || !"ollama".equalsIgnoreCase(config.provider())) {
            return;
        }
        String reportSummary = String.valueOf(summarizeReport(report)).replace('"', '\'');
        String prompt = "Issue: " + issue
                + "\nExecution context: " + debugExecutionContext(environment)
                + "\nDebug report: " + reportSummary
                + "\nUse this context for your follow-up response."
                + "\nProvide likely root cause, confidence, and next 3 checks.";
        String response = new OllamaAgentClient().generate(prompt, config);
        printSection("Debug Assistant");
        System.out.println(TerminalMarkdownRenderer.toTerminalText(response));
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
        printSection("Debug Inputs");
        System.out.println("Issue: " + issue);
        System.out.println("Project path: " + environment.getOrDefault("PRODBUDDY_PROJECT_PATH", ""));
        System.out.println("Code DB path: " + environment.getOrDefault("CODE_CONTEXT_DB_PATH", ""));
        System.out.println("Splunk auth mode: " + environment.getOrDefault("SPLUNK_AUTH_MODE", "token"));
        System.out.println("Splunk index: " + environment.getOrDefault("SPLUNK_DEFAULT_INDEX", ""));
        String codeHint = environment.getOrDefault("PRODBUDDY_CODE_HINT", "");
        if (!codeHint.isBlank()) {
            System.out.println("Code hint: " + codeHint);
        }
        System.out.println("Window minutes: " + environment.getOrDefault("DEBUG_WINDOW_MINUTES", "15"));
        System.out.println("Result limit: " + environment.getOrDefault("DEBUG_RESULT_LIMIT", "100"));
    }

    @SuppressWarnings("unchecked")
    private static void printDebugSummary(ToolResponse report) {
        printSection("Debug Summary");
        Map<String, Object> data = summarizeReport(report);
        System.out.println("Status: " + data.getOrDefault("status", "unknown"));
        System.out.println("Successful steps: " + data.getOrDefault("successfulSteps", 0));
        System.out.println("Failed steps: " + data.getOrDefault("failedSteps", 0));

        List<Map<String, Object>> failures = (List<Map<String, Object>>) data.getOrDefault("failures", List.of());
        if (!failures.isEmpty()) {
            System.out.println("Failures:");
            for (Map<String, Object> failure : failures) {
                Object rawErrors = failure.getOrDefault("errors", List.of());
                String detail = String.valueOf(rawErrors);
                System.out.println("- " + failure.getOrDefault("step", "unknown") + ": " + detail);
            }
        }

        List<Object> nextActions = (List<Object>) data.getOrDefault("nextActions", List.of());
        if (!nextActions.isEmpty()) {
            System.out.println("Next actions:");
            for (Object action : nextActions) {
                System.out.println("- " + action);
            }
        }
    }

    private static String debugExecutionContext(Map<String, String> environment) {
        return Map.of(
                "projectPath", environment.getOrDefault("PRODBUDDY_PROJECT_PATH", ""),
                "dbPath", environment.getOrDefault("CODE_CONTEXT_DB_PATH", ""),
                "codeHint", environment.getOrDefault("PRODBUDDY_CODE_HINT", ""),
                "windowMinutes", environment.getOrDefault("DEBUG_WINDOW_MINUTES", "15"),
                "resultLimit", environment.getOrDefault("DEBUG_RESULT_LIMIT", "100")
        ).toString();
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    private static Map<String, String> loadEnvironment() {
        Map<String, String> values = new LinkedHashMap<>(new EnvFileLoader().load(Path.of(".env")));
        values.putAll(System.getenv());
        return values;
    }
}
