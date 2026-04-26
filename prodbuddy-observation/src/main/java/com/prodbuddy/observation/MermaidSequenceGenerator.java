package com.prodbuddy.observation;

import java.util.List;

/**
 * Generates Mermaid Sequence Diagram syntax from ObservationEvents.
 */
public final class MermaidSequenceGenerator {

    /**
     * Converts a list of observation events to a Mermaid sequence diagram string.
     * @param events The events to process
     * @return Mermaid syntax string
     */
    public String generate(final List<ObservationEvent> events) {
        if (events == null || events.isEmpty()) {
            return "%% No sequence events recorded";
        }

        final int limit = 200;
        boolean truncated = events.size() > limit;
        List<ObservationEvent> limited = truncated ? events.subList(0, limit) : events;

        StringBuilder sb = new StringBuilder();
        sb.append("sequenceDiagram\n");
        sb.append("    autonumber\n");

        appendParticipants(sb, limited);
        appendEvents(sb, limited);
        appendTruncationNote(sb, limited, truncated, limit);

        return sb.toString();
    }

    private void appendParticipants(StringBuilder sb, List<ObservationEvent> limited) {
        java.util.Set<String> actors = new java.util.HashSet<>();
        for (ObservationEvent event : limited) {
            actors.add(event.getSender());
            actors.add(event.getReceiver());
        }

        for (String actor : actors) {
            sb.append("    participant ").append(quote(actor))
              .append(" as ").append(getSafeActorId(actor)).append("\n");
        }
    }

    private void appendEvents(StringBuilder sb, List<ObservationEvent> limited) {
        for (ObservationEvent event : limited) {
            String s = getSafeActorId(event.getSender());
            String r = getSafeActorId(event.getReceiver());
            String m = sanitizeLabel(event.getMethod());
            String a = sanitizeLabel(event.getAction());

            sb.append("    ").append(s).append("->>").append(r)
              .append(": ").append(m);
            if (a != null && !a.isEmpty()) {
                sb.append(" (").append(a).append(")");
            }
            sb.append("\n");
        }
    }

    private void appendTruncationNote(StringBuilder sb, List<ObservationEvent> limited, 
                                      boolean truncated, int limit) {
        if (truncated) {
            sb.append("    Note over ").append(getSafeActorId(limited.get(0).getSender()))
              .append(", ").append(getSafeActorId(limited.get(limited.size() - 1).getReceiver()))
              .append(": ... trace truncated after ").append(limit).append(" steps ...\n");
        }
    }

    private String getSafeActorId(final String name) {
        if (name == null) return "unknown";
        // Actor IDs must be alphanumeric
        return "actor_" + name.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private String quote(final String text) {
        if (text == null) return "\"\"";
        return "\"" + text.replace("\"", "'") + "\"";
    }

    private String sanitizeLabel(final String text) {
        if (text == null) return "";
        // Allow most characters but escape or remove those that definitely break Mermaid labels
        return text.replace("\n", " ")
                   .replace("\r", " ")
                   .replace("->>", "->")
                   .replace("\"", "'")
                   .trim();
    }

    private String sanitize(final String text, final int maxLength) {
        if (text == null) return "unknown";
        
        // Keep for backward compatibility if needed, but we use sanitizeLabel/getSafeActorId now
        String cleaned = text.replaceAll("[^a-zA-Z0-9\\s._\\-:/]", "")
                             .replace("\n", " ")
                             .replace("\r", " ")
                             .trim();
                             
        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength - 3) + "...";
        }
        return cleaned;
    }
}
