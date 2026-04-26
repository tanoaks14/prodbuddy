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
        sb.append("%%{init: {'theme': 'neutral', 'themeVariables': { 'fontSize': '14px', 'fontFamily': 'arial' }}}%%\n");
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
        Map<String, String> actorToDisplayName = new java.util.HashMap<>();

        collectStyling(actorToColor, actorToDisplayName, limited);

        for (Map.Entry<String, String> entry : actorToColor.entrySet()) {
            String actor = entry.getKey();
            String color = entry.getValue();
            String displayName = actorToDisplayName.getOrDefault(actor, actor);

            sb.append("    participant ").append(getSafeActorId(actor))
              .append(" as ").append(quote(displayName));
            if (!color.isEmpty()) {
                sb.append(" ").append(color);
            }
            sb.append("\n");
        }
    }

    private void collectStyling(final Map<String, String> colorMap,
                                final Map<String, String> nameMap,
                                final List<ObservationEvent> limited) {
        for (ObservationEvent event : limited) {
            updateActorStyling(colorMap, nameMap,
                    event.getSender(), event.getMetadata());
            updateActorStyling(colorMap, nameMap,
                    event.getReceiver(), event.getMetadata());
        }
        for (ObservationEvent event : limited) {
            applyDefaultStylingIfMissing(colorMap, nameMap,
                    event.getSender());
            applyDefaultStylingIfMissing(colorMap, nameMap,
                    event.getReceiver());
        }
    }

    private void updateActorStyling(final Map<String, String> colorMap,
                                    final Map<String, String> nameMap,
                                    final String actor,
                                    final Map<String, String> meta) {
        String styledActor = meta.get("styledActor");
        if (styledActor != null && !styledActor.equalsIgnoreCase(actor)) {
            return;
        }

        String color = meta.getOrDefault("actorColor", "");
        if (!color.isEmpty()) {
            colorMap.put(actor, color);
        } else if (!colorMap.containsKey(actor)) {
            colorMap.put(actor, "");
        }

        String displayName = meta.getOrDefault("displayName", "");
        if (!displayName.isEmpty()) {
            nameMap.put(actor, displayName);
        }
    }

    private void applyDefaultStylingIfMissing(
            final Map<String, String> colorMap,
            final Map<String, String> nameMap,
            final String actor) {
        ObservationStyling.Styling s = ObservationStyling.get(actor);
        if (s != null) {
            if (colorMap.getOrDefault(actor, "").isEmpty()) {
                colorMap.put(actor, s.getColor());
            }
            if (!nameMap.containsKey(actor)) {
                nameMap.put(actor, s.getDisplayName());
            }
        }
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
        String wrapped = wrap(noteText, 80);
        String text = wrapped.replace("\n", "<br/>");
        sb.append(sanitizeLabel(text));
        if (!noteColor.isEmpty()) {
            sb.append("</span>");
        }
        sb.append("\n");
    }

    private String wrap(final String text, final int width) {
        if (text == null || text.length() <= width) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        int lastSpace = -1;
        int lineStart = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                lastSpace = i;
            } else if (c == '\n') {
                lineStart = i + 1;
                lastSpace = -1;
            }
            if (i - lineStart >= width) {
                if (lastSpace != -1) {
                    sb.append(text, lineStart, lastSpace).append("\n");
                    lineStart = lastSpace + 1;
                    lastSpace = -1;
                } else {
                    sb.append(text, lineStart, i).append("\n");
                    lineStart = i;
                }
            }
        }
        sb.append(text.substring(lineStart));
        return sb.toString();
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
            String err = "<span style='color:red'>!! " + a + " !!</span>";
            sb.append("    Note right of ").append(s)
              .append(": ").append(err).append("\n");
        }

        String arrow = getMermaidArrow(m, a, isError);
        sb.append("    ").append(s).append(arrow).append(r)
          .append(": ").append(m);
        if (!a.isEmpty()) {
            sb.append(" (").append(a).append(")");
        }
        sb.append("\n");
    }

    private String getMermaidArrow(final String m, final String a,
                                   final boolean isError) {
        if (isError) {
            return "--x";
        }
        String lowerM = m.toLowerCase();
        String lowerA = a.toLowerCase();
        if (lowerM.contains("response") || lowerM.contains("result")
                || lowerA.contains("success") || lowerA.contains("failed")) {
            return "-->>";
        }
        return "->>";
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
