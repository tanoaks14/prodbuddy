package com.prodbuddy.tools.kubectl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

public final class KubectlTool implements Tool {

    private static final String NAME = "kubectl";
    private final KubectlCommandBuilder commandBuilder;
    private final KubectlOperationGuard operationGuard;
    private final SequenceLogger seqLog;

    public KubectlTool(KubectlCommandBuilder commandBuilder) {
        this.commandBuilder = commandBuilder;
        this.operationGuard = new KubectlOperationGuard();
        this.seqLog = new Slf4jSequenceLogger(KubectlTool.class);
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                NAME,
                "Kubectl preview and execution tool",
                Set.of("kubectl.get", "kubectl.describe", "kubectl.logs", "kubectl.apply", "kubectl.raw", "kubectl.command")
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        return "kubectl".equalsIgnoreCase(request.intent()) || "kubernetes".equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        seqLog.logSequence("AgentLoopOrchestrator", "kubectl", "execute", "Executing kubectl " + request.operation());
        String namespace = context.envOrDefault("KUBECTL_NAMESPACE", "default");
        List<String> command = commandBuilder.build(request.operation(), request.payload(), namespace);
        if (!operationGuard.isAllowed(request.operation(), command)) {
            seqLog.logSequence("kubectl", "AgentLoopOrchestrator", "execute", "Blocked by read-only guard");
            return ToolResponse.failure("KUBECTL_READ_ONLY", "operation/command not allowed in read-only mode");
        }
        boolean execute = Boolean.parseBoolean(String.valueOf(
                request.payload().getOrDefault("execute", context.envOrDefault("KUBECTL_EXECUTE", "false"))
        ));
        if (!execute) {
            seqLog.logSequence("kubectl", "AgentLoopOrchestrator", "execute", "Preview only (no exec)");
            return ToolResponse.ok(Map.of("command", String.join(" ", command), "executed", false));
        }
        seqLog.logSequence("kubectl", "KubernetesCluster", "run", "Running command");
        int timeoutSeconds = Integer.parseInt(context.envOrDefault("KUBECTL_TIMEOUT_SECONDS", "20"));
        final boolean noTruncate = Boolean.parseBoolean(String.valueOf(request.payload().getOrDefault("noTruncate", "false")));
        final int maxOutputChars = noTruncate ? Integer.MAX_VALUE : Integer.parseInt(String.valueOf(request.payload().getOrDefault("maxOutputChars", 
                context.envOrDefault("KUBECTL_MAX_OUTPUT_CHARS", "20000"))));
        return run(command, timeoutSeconds, maxOutputChars);
    }

    private ToolResponse run(List<String> command, int timeoutSeconds, int maxOutputChars) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return ToolResponse.failure("KUBECTL_TIMEOUT", "command timed out after " + timeoutSeconds + " seconds");
            }
            String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines()
                    .collect(Collectors.joining(System.lineSeparator()));
            int exitCode = process.exitValue();
            String safeOutput = truncate(output, maxOutputChars);
            return ToolResponse.ok(Map.of(
                    "command", String.join(" ", command),
                    "executed", true,
                    "exitCode", exitCode,
                    "output", safeOutput,
                    "truncated", output.length() > maxOutputChars
            ));
        } catch (Exception exception) {
            return ToolResponse.failure("KUBECTL_FAILED", exception.getMessage());
        }
    }

    private String truncate(String output, int maxOutputChars) {
        if (output == null || output.length() <= maxOutputChars) {
            return output;
        }
        return output.substring(0, maxOutputChars);
    }
}
