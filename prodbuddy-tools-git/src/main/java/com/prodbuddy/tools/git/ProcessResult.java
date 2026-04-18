package com.prodbuddy.tools.git;

/** Immutable result of a shell command execution. */
public record ProcessResult(int exitCode, String stdout, String stderr) {

    public boolean isSuccess() {
        return exitCode == 0;
    }

    public String[] lines() {
        if (stdout == null || stdout.isBlank()) {
            return new String[0];
        }
        return stdout.split("\\R");
    }
}
