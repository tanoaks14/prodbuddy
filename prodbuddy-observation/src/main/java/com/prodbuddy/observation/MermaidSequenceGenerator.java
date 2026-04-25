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
    public String generate(List<ObservationEvent> events) {
        if (events == null || events.isEmpty()) {
            return "%% No sequence events recorded";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("sequenceDiagram\n");
        sb.append("    autonumber\n");

        for (ObservationEvent event : events) {
            String s = sanitize(event.getSender());
            String r = sanitize(event.getReceiver());
            String m = sanitize(event.getMethod());
            String a = sanitize(event.getAction());

            sb.append("    ").append(s).append("->>").append(r)
              .append(": ").append(m);
            if (a != null && !a.isEmpty()) {
                sb.append(" (").append(a).append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String sanitize(String text) {
        if (text == null) return "unknown";
        // Remove characters that break mermaid syntax
        return text.replace("\"", "")
                   .replace("(", "[")
                   .replace(")", "]")
                   .replace("\n", " ")
                   .trim();
    }
}
