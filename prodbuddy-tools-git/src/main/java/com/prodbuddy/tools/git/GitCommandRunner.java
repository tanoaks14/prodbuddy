package com.prodbuddy.tools.git;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Shells out to the local git binary to run read-only queries. */
public final class GitCommandRunner {

    /** Run the given git subcommand in the specified repository directory. */
    public ProcessResult run(String repoPath, String... args) {
        List<String> cmd = buildCommand(repoPath, args);
        try {
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.directory(new java.io.File(repoPath));
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            Process process = builder.start();
            String out = drain(process.getInputStream());
            String err = drain(process.getErrorStream());
            int code = process.waitFor();
            return new ProcessResult(code, out, err);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new ProcessResult(-1, "", ex.getMessage());
        } catch (Exception ex) {
            return new ProcessResult(-1, "", ex.getMessage());
        }
    }

    /** Extract changed file paths from a `git diff --name-only` output. */
    public List<String> parseChangedFiles(String diffOutput) {
        List<String> files = new ArrayList<>();
        if (diffOutput == null || diffOutput.isBlank()) {
            return files;
        }
        for (String line : diffOutput.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                files.add(trimmed);
            }
        }
        return files;
    }

    private List<String> buildCommand(String repoPath, String[] args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        for (String arg : args) {
            cmd.add(arg);
        }
        return cmd;
    }

    private String drain(java.io.InputStream stream) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
