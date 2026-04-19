package com.prodbuddy.context;

import java.util.List;
import java.util.Map;

/**
 * Formats a {@link ConversationContext} into human-readable text suitable
 * for inclusion in an LLM prompt.
 *
 * <p>Produces a structured, token-efficient summary of every recorded
 * tool invocation: tool name, operation, key request parameters, response
 * highlights, HTTP status codes, duration, and any errors.
 */
public final class ContextFormatter {

    private static final int MAX_FIELD_LEN = 600;

    private ContextFormatter() {
    }

    /**
     * Formats the full context into a prompt-ready string.
     *
     * @param context the collected invocations
     * @return a multi-line string ready to embed in an LLM prompt
     */
    public static String format(ConversationContext context) {
        List<ToolInvocation> invocations = context.invocations();
        if (invocations.isEmpty()) {
            return "(no tool invocations were recorded)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Tool Invocation Context (session=")
                .append(context.sessionId()).append(")\n");
        sb.append("Total calls: ").append(invocations.size())
                .append(", Successful: ").append(context.successful().size())
                .append(", Failed: ").append(context.failed().size())
                .append("\n\n");
        for (int i = 0; i < invocations.size(); i++) {
            appendInvocation(sb, i + 1, invocations.get(i));
        }
        return sb.toString();
    }

    private static void appendInvocation(
            StringBuilder sb, int idx, ToolInvocation inv
    ) {
        sb.append("### [").append(idx).append("] ")
                .append(inv.tool()).append(".").append(inv.operation())
                .append(" (").append(inv.durationMs()).append("ms, ")
                .append(inv.success() ? "OK" : "FAILED").append(")\n");
        appendRequestHighlights(sb, inv);
        if (inv.success()) {
            appendResponseHighlights(sb, inv);
        } else {
            sb.append("  error: ").append(inv.errorSummary()).append("\n");
        }
        sb.append("\n");
    }

    private static void appendRequestHighlights(
            StringBuilder sb, ToolInvocation inv
    ) {
        Map<String, Object> payload = inv.requestPayload();
        if (payload == null || payload.isEmpty()) {
            return;
        }
        sb.append("  request:");
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                sb.append("\n    ").append(entry.getKey()).append("=")
                        .append(truncate(String.valueOf(entry.getValue())));
            }
        }
        sb.append("\n");
    }

    private static void appendResponseHighlights(
            StringBuilder sb, ToolInvocation inv
    ) {
        Map<String, Object> data = inv.responseData();
        if (data == null || data.isEmpty()) {
            return;
        }
        sb.append("  response:");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                sb.append("\n    ").append(entry.getKey()).append("=")
                        .append(truncate(String.valueOf(entry.getValue())));
            }
        }
        sb.append("\n");
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_FIELD_LEN) {
            return value;
        }
        return value.substring(0, MAX_FIELD_LEN) + "...[truncated]";
    }
}
