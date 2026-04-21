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
import com.prodbuddy.context.ContextCollector;
import com.prodbuddy.context.ContextFormatter;
import com.prodbuddy.context.ConversationContext;
import com.prodbuddy.orchestrator.AgentLoopOrchestrator;
import com.prodbuddy.orchestrator.LoopConfig;
import com.prodbuddy.orchestrator.RuleBasedToolRouter;

public final class ProdBuddyApplication {

    public static void main(String[] args) {
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        String[] effectiveArgs = splitClumpedArgs(args);
        ToolBootstrap bootstrap = new ToolBootstrap();
        ToolRegistry registry = bootstrap.createRegistry();
        AgentLoopOrchestrator orchestrator = new AgentLoopOrchestrator(
                registry, new RuleBasedToolRouter(), LoopConfig.defaults()
        );
        Map<String, String> environment = loadEnvironment();
        com.prodbuddy.core.agent.AgentConfig agentConfig = com.prodbuddy.core.agent.AgentConfig.from(environment);
        if (isRecipeMode(effectiveArgs)) {
            RecipeCliHandler.handle(effectiveArgs, orchestrator, environment, agentConfig);
            return;
        }
        if (isDebugIssueMode(effectiveArgs)) {
            System.out.println("Agent provider: " + agentConfig.summary());
            runDebugIssueLoop(effectiveArgs, orchestrator, environment, agentConfig);
            return;
        }
        runDefaultMode(effectiveArgs, orchestrator, environment, agentConfig, registry);
    }

    private static String[] splitClumpedArgs(String[] args) {
        if (args.length == 1 && (args[0].contains(" ") || args[0].contains("="))) {
            return args[0].split("\\s+");
        }
        return args;
    }

    private static void runDefaultMode(
            String[] args,
            AgentLoopOrchestrator orchestrator,
            Map<String, String> environment,
            com.prodbuddy.core.agent.AgentConfig agentConfig,
            ToolRegistry registry
    ) {
        printSection("Runtime");
        System.out.println("Agent provider: " + agentConfig.summary());
        System.out.println("Registered tools: " + registry.metadata().size());
        runLocalLlmIfEnabled(args, agentConfig, registry.metadata());
        ToolRequest request = new ToolRequest("pdf", "read", Map.of("path", "sample.pdf"));
        ToolContext context = new ToolContext(UUID.randomUUID().toString(), environment);
        ConversationContext convCtx = new ConversationContext(context.requestId());
        ContextCollector collector = new ContextCollector(orchestrator::run, convCtx);
        printSection("Sample Run");
        System.out.println(collector.execute(request, context));
        writeContextFile("default-run-context.md", ContextFormatter.format(convCtx));
    }

    private static void runLocalLlmIfEnabled(String[] args, com.prodbuddy.core.agent.AgentConfig config, List<ToolMetadata> tools) {
        if (!config.enabled() || !"ollama".equalsIgnoreCase(config.provider())) {
            return;
        }
        String userPrompt = args.length > 0 ? String.join(" ", args) : "Summarize available tools in one paragraph.";
        String prompt = buildToolAwarePrompt(userPrompt, tools);
        String response = new com.prodbuddy.core.agent.OllamaAgentClient().generate(prompt, config);
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
            com.prodbuddy.core.agent.AgentConfig agentConfig
    ) {
        DebugIssueHandler.handle(args, orchestrator, environment, agentConfig);
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    private static void writeContextFile(String path, String data) {
        try {
            java.nio.file.Files.writeString(Path.of(path), data);
            System.out.println("Detailed execution context saved to: " + path);
        } catch (java.io.IOException e) {
            System.err.println("Failed to write context file: " + e.getMessage());
        }
    }

    private static Map<String, String> loadEnvironment() {
        Map<String, String> values = new LinkedHashMap<>(new EnvFileLoader().load(Path.of(".env")));
        values.putAll(System.getenv());
        return values;
    }
}
