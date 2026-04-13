package com.prodbuddy.app;

import java.util.Scanner;

/**
 * Thin wrapper for console I/O, shared by any app class that needs user prompts.
 * Uses System.console() when available (supports secret/masked input),
 * falls back to a shared Scanner for non-interactive environments.
 */
final class ConsoleInput {

    private static final Scanner SCANNER = new Scanner(System.in);

    private ConsoleInput() {
    }

    static String readLine(String prompt) {
        java.io.Console console = System.console();
        if (console != null) {
            return console.readLine("%s", prompt);
        }
        System.out.print(prompt);
        return SCANNER.nextLine();
    }

    static String readSecret(String prompt) {
        java.io.Console console = System.console();
        if (console != null) {
            char[] value = console.readPassword("%s", prompt);
            return value == null ? "" : new String(value);
        }
        System.out.print(prompt);
        return SCANNER.nextLine();
    }
}
