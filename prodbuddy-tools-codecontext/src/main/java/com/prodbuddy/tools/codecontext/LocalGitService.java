package com.prodbuddy.tools.codecontext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight service to extract Git-based risk signals for source files.
 */
public final class LocalGitService {

    /**
     * Gets high-level risk metadata for a file.
     */
    public Map<String, Object> getRiskMetadata(final String filePath) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        try {
            metadata.put("lastCommit", getLastCommitInfo(filePath));
            metadata.put("churnCount", getCommitCount(filePath));
        } catch (Exception e) {
            metadata.put("gitError", e.getMessage());
        }
        // Fallback/Augment with FS metadata
        try {
            java.nio.file.Path path = java.nio.file.Path.of(filePath);
            if (java.nio.file.Files.exists(path)) {
                metadata.put("lastModified", java.nio.file.Files.getLastModifiedTime(path).toString());
                metadata.put("sizeBytes", java.nio.file.Files.size(path));
            }
        } catch (Exception fsEx) {
            metadata.put("fsError", fsEx.getMessage());
        }
        return metadata;
    }

    private Map<String, String> getLastCommitInfo(final String filePath) throws Exception {
        String output = runGitCommand("log", "-n", "1", "--format=%an|%ar|%s", "--", filePath);
        String[] parts = output.split("\\|");
        Map<String, String> info = new LinkedHashMap<>();
        if (parts.length >= 3) {
            info.put("author", parts[0]);
            info.put("age", parts[1]);
            info.put("subject", parts[2]);
        }
        return info;
    }

    private int getCommitCount(final String filePath) throws Exception {
        String output = runGitCommand("rev-list", "--count", "HEAD", "--", filePath);
        return Integer.parseInt(output.trim());
    }

    private String runGitCommand(String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git");
        for (String arg : args) {
            pb.command().add(arg);
        }
        Process process = pb.start();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroy();
            throw new RuntimeException("Git command timed out");
        }
        if (process.exitValue() != 0) {
             return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            return line != null ? line : "";
        }
    }
}
