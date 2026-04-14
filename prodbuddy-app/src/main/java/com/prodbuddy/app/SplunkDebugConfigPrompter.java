package com.prodbuddy.app;

import java.util.Map;

final class SplunkDebugConfigPrompter {

    private SplunkDebugConfigPrompter() {
    }

    static void prompt(Map<String, String> environment) {
        String authMode = resolveAuthMode(environment);
        promptCredentials(environment, authMode);
        promptIndex(environment);
    }

    private static String resolveAuthMode(Map<String, String> environment) {
        String mode = environment.getOrDefault("SPLUNK_AUTH_MODE", "").trim().toLowerCase();
        if (mode.isBlank()) {
            mode = promptWithDefault("Splunk auth mode (token/user/sso)", "token").trim().toLowerCase();
        }
        if (mode.isBlank()) {
            mode = "token";
        }
        environment.put("SPLUNK_AUTH_MODE", mode);
        return mode;
    }

    private static void promptCredentials(Map<String, String> environment, String authMode) {
        if ("user".equals(authMode)) {
            promptIfMissing(environment, "SPLUNK_USERNAME", false);
            promptIfMissing(environment, "SPLUNK_PASSWORD", true);
            return;
        }
        if ("sso".equals(authMode) || "session".equals(authMode)) {
            promptIfMissing(environment, "SPLUNK_SESSION_KEY", true);
            if (environment.getOrDefault("SPLUNK_SESSION_KEY", "").isBlank()) {
                System.out.println("Splunk SSO selected but no session key was provided. Add SPLUNK_SESSION_KEY to continue.");
            }
            return;
        }
        promptIfMissing(environment, "SPLUNK_TOKEN", true);
        if (environment.getOrDefault("SPLUNK_TOKEN", "").isBlank()) {
            System.out.println("No Splunk token provided. If your org uses SSO, choose auth mode 'sso' and provide SPLUNK_SESSION_KEY.");
        }
    }

    private static void promptIndex(Map<String, String> environment) {
        String index = promptWithDefault("Splunk index for debug searches", environment.getOrDefault("SPLUNK_DEFAULT_INDEX", "main"));
        if (!index.isBlank()) {
            environment.put("SPLUNK_DEFAULT_INDEX", index);
        }
    }

    private static void promptIfMissing(Map<String, String> environment, String key, boolean secret) {
        String existing = environment.get(key);
        if (existing != null && !existing.isBlank()) {
            return;
        }
        String prompt = "Missing " + key + ". Enter value (or press Enter to skip): ";
        String input = secret ? ConsoleInput.readSecret(prompt) : ConsoleInput.readLine(prompt);
        if (input != null && !input.isBlank()) {
            environment.put(key, input.trim());
        }
    }

    private static String promptWithDefault(String label, String defaultValue) {
        String value = defaultValue == null ? "" : defaultValue.trim();
        String prompt = label + (value.isBlank() ? ": " : " [" + value + "]: ");
        String input = ConsoleInput.readLine(prompt);
        if (input == null || input.isBlank()) {
            return value;
        }
        return input.trim();
    }
}
