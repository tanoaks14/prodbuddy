package com.prodbuddy.tools.git;

import com.prodbuddy.core.tool.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only git introspection tool: diff, status, log. */
public final class GitTool implements Tool {

    private static final String NAME = "git";
    private final GitCommandRunner runner;

    public GitTool() {
        this.runner = new GitCommandRunner();
    }

    @Override
    public com.prodbuddy.core.tool.ToolStyling styling() {
        return new com.prodbuddy.core.tool.ToolStyling("#CFD8DC", "#37474F", "#ECEFF1", "🐙 Git", java.util.Map.of());
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(NAME, "Read-only Git introspection tool",
                Set.of("git.diff", "git.status", "git.log"));
    }

    @Override
    public boolean supports(ToolRequest request) {
        return NAME.equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) {
        String repoPath = String.valueOf(
                request.payload().getOrDefault("repoPath", context.envOrDefault("GIT_REPO_PATH", ".")));
        String op = request.operation().toLowerCase();
        com.prodbuddy.observation.ObservationContext.log("Orchestrator", "Git", op, "executing", 
                styling().toMetadata("Git"));
        return switch (op) {
            case "diff" -> handleDiff(request, repoPath);
            case "status" -> handleStatus(repoPath);
            case "log" -> handleLog(request, repoPath);
            default -> ToolResponse.failure("GIT_UNSUPPORTED", "Supported: diff, status, log");
        };
    }

    private ToolResponse handleDiff(ToolRequest request, String repoPath) {
        String base = String.valueOf(request.payload().getOrDefault("base", "HEAD~1"));
        ProcessResult result = runner.run(repoPath, "diff", "--name-only", base);
        if (!result.isSuccess()) {
            return ToolResponse.failure("GIT_DIFF_FAILED", result.stderr());
        }
        List<String> changed = runner.parseChangedFiles(result.stdout());
        String firstJava = changed.stream().filter(f -> f.endsWith(".java")).findFirst().orElse("");
        String firstClass = toClassName(firstJava);
        return ToolResponse.ok(Map.of(
                "base", base, "changedFiles", changed,
                "changedCount", changed.size(),
                "firstChangedFile", firstJava,
                "firstChangedClass", firstClass
        ));
    }

    private ToolResponse handleStatus(String repoPath) {
        ProcessResult result = runner.run(repoPath, "status", "--short");
        if (!result.isSuccess()) {
            return ToolResponse.failure("GIT_STATUS_FAILED", result.stderr());
        }
        return ToolResponse.ok(Map.of("statusOutput", result.stdout(),
                "lines", List.of(result.lines())));
    }

    private ToolResponse handleLog(ToolRequest request, String repoPath) {
        String n = String.valueOf(request.payload().getOrDefault("n", "10"));
        ProcessResult result = runner.run(repoPath, "log", "--oneline", "-n", n);
        if (!result.isSuccess()) {
            return ToolResponse.failure("GIT_LOG_FAILED", result.stderr());
        }
        return ToolResponse.ok(Map.of("log", result.stdout(), "lines", List.of(result.lines())));
    }

    private String toClassName(String javaFilePath) {
        if (javaFilePath == null || javaFilePath.isBlank()) {
            return "";
        }
        String normalized = javaFilePath.replace('\\', '/');
        int src = normalized.indexOf("src/main/java/");
        String relative = src >= 0 ? normalized.substring(src + "src/main/java/".length()) : normalized;
        return relative.replace('/', '.').replaceAll("\\.java$", "");
    }
}
