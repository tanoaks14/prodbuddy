package com.prodbuddy.observation;

import java.util.List;
import java.util.Map;

/**
 * Generates Mermaid Sequence Diagram syntax from ObservationEvents.
 */
public final class MermaidSequenceGenerator {

    /** Default event limit. */
    private static final int DEFAULT_LIMIT = 200;
    /** Truncation suffix length. */
    private static final int TRUNC_SUFFIX = 3;

    /**
     * Converts a list of observation events to a Mermaid sequence diagram.
     * @param events The events to process
     * @return Mermaid syntax string
     */
    public String generate(final List<ObservationEvent> events) {
        if (events == null || events.isEmpty()) {
            return "%% No sequence events recorded";
        }

        final int limit = DEFAULT_LIMIT;
        boolean truncated = events.size() > limit;
        List<ObservationEvent> limited = truncated
                ? events.subList(0, limit) : events;

        StringBuilder sb = new StringBuilder();
        sb.append("sequenceDiagram\n");
        sb.append("    autonumber\n");

        appendParticipants(sb, limited);
        appendEvents(sb, limited);
        appendTruncationNote(sb, limited, truncated, limit);

        return sb.toString();
    }

    private void appendParticipants(final StringBuilder sb,
                                   final List<ObservationEvent> limited) {
        Map<String, String> actorToColor = new java.util.HashMap<>();
        // First pass: collect all colors from metadata
        for (ObservationEvent event : limited) {
            updateActorColor(actorToColor, event.getSender(),
                    event.getMetadata());
            updateActorColor(actorToColor, event.getReceiver(),
                    event.getMetadata());
        }
        // Second pass: apply defaults for those still empty
        for (ObservationEvent event : limited) {
            applyDefaultColorIfMissing(actorToColor, event.getSender());
            applyDefaultColorIfMissing(actorToColor, event.getReceiver());
        }

        for (Map.Entry<String, String> entry : actorToColor.entrySet()) {
            String actor = entry.getKey();
            String color = entry.getValue();
            sb.append("    participant ").append(getSafeActorId(actor))
              .append(" as ").append(quote(actor));
            if (!color.isEmpty()) {
                sb.append(" ").append(color);
            }
            sb.append("\n");
        }
    }

    private void updateActorColor(final Map<String, String> map,
                                  final String actor,
                                  final Map<String, String> meta) {
        String color = meta.getOrDefault("actorColor", "");
        if (!color.isEmpty()) {
            map.put(actor, color);
        } else if (!map.containsKey(actor)) {
            map.put(actor, "");
        }
    }

    private void applyDefaultColorIfMissing(final Map<String, String> map,
                                            final String actor) {
        if (map.getOrDefault(actor, "").isEmpty()) {
            map.put(actor, getDefaultColorForTool(actor));
        }
    }

    private String getDefaultColorForTool(final String tool) {
        String lower = tool.toLowerCase();
        if (lower.contains("agent")) { return "#D1C4E9"; }
        if (lower.contains("newrelic")) { return "#B2DFDB"; }
        if (lower.contains("nerdgraph")) { return "#E0F2F1"; }
        if (lower.contains("splunkapi")) { return "#FBE9E7"; }
        if (lower.contains("splunk")) { return "#FFCCBC"; }
        if (lower.contains("interactive")) { return "#FFF9C4"; }
        if (lower.contains("elasticcluster")) { return "#E1F5FE"; }
        if (lower.contains("elasticsearch")) { return "#B3E5FC"; }
        if (lower.contains("observation")) { return "#F5F5F5"; }
        return "";
    }

    private void appendEvents(final StringBuilder sb,
                              final List<ObservationEvent> limited) {
        for (ObservationEvent event : limited) {
            final Map<String, String> meta = event.getMetadata();
            final String type = meta.getOrDefault("type", "");
            final String style = meta.getOrDefault("style", "");

            if (isNoteType(type, style, event)) {
                appendNote(sb, event);
            } else {
                appendArrow(sb, event, style);
            }
        }
    }

    private boolean isNoteType(final String type, final String style,
                               final ObservationEvent event) {
        return "note".equals(type) || "thinking".equals(style)
                || "query".equals(style) || isThinking(event);
    }

    private void appendNote(final StringBuilder sb,
                            final ObservationEvent event) {
        final Map<String, String> meta = event.getMetadata();
        final String s = getSafeActorId(event.getSender());
        final String m = sanitizeLabel(event.getMethod());
        final String a = sanitizeLabel(event.getAction());
        final String noteText = meta.getOrDefault("noteText",
                m + (a.isEmpty() ? "" : ": " + a));
        
        final String noteColor = meta.getOrDefault("noteColor", "");
        final String textColor = meta.getOrDefault("textColor", "black");
        
        sb.append("    Note over ").append(s).append(": ");
        if (!noteColor.isEmpty()) {
            sb.append("<span style='background-color:").append(noteColor)
              .append("; color:").append(textColor).append("; padding:2px;'>");
        }
        sb.append(sanitizeLabel(noteText));
        if (!noteColor.isEmpty()) {
            sb.append("</span>");
        }
        sb.append("\n");
    }

    private void appendArrow(final StringBuilder sb,
                             final ObservationEvent event,
                             final String style) {
        final String s = getSafeActorId(event.getSender());
        final String r = getSafeActorId(event.getReceiver());
        final String m = sanitizeLabel(event.getMethod());
        final String a = sanitizeLabel(event.getAction());

        final boolean isError = "error".equals(style)
                || (a.startsWith("HTTP ") && !a.startsWith("HTTP 2"));
        if (isError) {
            sb.append("    Note right of ").append(s)
              .append(": <span style='color:red'>!! ")
              .append(a).append(" !!</span>\n");
        }

        final String arrow = isError ? "--x" : "->>";
        sb.append("    ").append(s).append(arrow).append(r)
          .append(": ").append(m);
        if (!a.isEmpty()) {
            sb.append(" (").append(a).append(")");
        }
        sb.append("\n");
    }

    private boolean isThinking(final ObservationEvent event) {
        final String m = event.getMethod().toLowerCase();
        return m.contains("think") || m.contains("analyze")
                || m.contains("opinion");
    }

    private void appendTruncationNote(final StringBuilder sb,
                                      final List<ObservationEvent> limited,
                                      final boolean truncated,
                                      final int limit) {
        if (truncated) {
            String s = getSafeActorId(limited.get(0).getSender());
            String r = getSafeActorId(limited.get(limited.size() - 1)
                    .getReceiver());
            sb.append("    Note over ").append(s).append(", ").append(r)
              .append(": ... trace truncated after ").append(limit)
              .append(" steps ...\n");
        }
    }

    private String getSafeActorId(final String name) {
        if (name == null) {
            return "unknown";
        }
        return "actor_" + name.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private String quote(final String text) {
        if (text == null) {
            return "\"\"";
        }
        return "\"" + text.replace("\"", "'") + "\"";
    }

    private String sanitizeLabel(final String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\n", " ")
                   .replace("\r", " ")
                   .replace("->>", "->")
                   .replace("\"", "'")
                   .trim();
    }

    private String sanitize(final String text, final int maxLength) {
        if (text == null) {
            return "unknown";
        }
        String cleaned = text.replaceAll("[^a-zA-Z0-9\\s._\\-:/]", "")
                             .replace("\n", " ")
                             .replace("\r", " ")
                             .trim();
        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength - TRUNC_SUFFIX) + "...";
        }
        return cleaned;
    }
}
