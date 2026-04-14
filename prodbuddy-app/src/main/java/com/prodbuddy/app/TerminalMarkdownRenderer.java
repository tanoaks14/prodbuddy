package com.prodbuddy.app;

import java.util.regex.Pattern;

final class TerminalMarkdownRenderer {

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(.*?)\\1");
    private static final Pattern ITALIC = Pattern.compile("(\\*|_)(.*?)\\1");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern MULTI_BLANK = Pattern.compile("\\n{3,}");

    private TerminalMarkdownRenderer() {
    }

    static String toTerminalText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder builder = new StringBuilder();
        boolean inCodeFence = false;
        for (String rawLine : normalized.split("\\n", -1)) {
            String line = rawLine;
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCodeFence = !inCodeFence;
                continue;
            }
            if (!inCodeFence) {
                line = HEADING.matcher(line).replaceFirst("");
                line = line.replaceFirst("^>\\s?", "");
                line = line.replaceFirst("^\\s*[-*]\\s+", "- ");
            }
            line = LINK.matcher(line).replaceAll("$1 ($2)");
            line = BOLD.matcher(line).replaceAll("$2");
            line = ITALIC.matcher(line).replaceAll("$2");
            line = INLINE_CODE.matcher(line).replaceAll("$1");
            builder.append(line).append('\n');
        }
        String compact = MULTI_BLANK.matcher(builder.toString().trim()).replaceAll("\n\n");
        return compact.trim();
    }
}
