package com.prodbuddy.observation;

import net.sourceforge.plantuml.SourceStringReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Native Java renderer using PlantUML.
 */
public final class LocalDiagramRenderer {

    /** Max events. */
    private static final int MAX_EVENTS = 50;
    /** Max sender length. */
    private static final int MAX_SENDER = 30;
    /** Max method length. */
    private static final int MAX_METHOD = 50;
    /** Max action length. */
    private static final int MAX_ACTION = 100;
    /** Truncation suffix. */
    private static final int TRUNC_SUFFIX = 3;

    /**
     * Renders a list of events to a PNG.
     * @param events The events to render
     * @return PNG bytes
     * @throws IOException if rendering fails
     */
    public byte[] renderSequence(final List<ObservationEvent> events)
            throws IOException {
        if (events == null || events.isEmpty()) {
            return new byte[0];
        }

        List<ObservationEvent> limited = events.size() > MAX_EVENTS
            ? events.subList(events.size() - MAX_EVENTS, events.size())
                : events;

        String plantUml = buildPlantUml(limited);

        SourceStringReader reader = new SourceStringReader(plantUml);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            reader.generateImage(os);
            return os.toByteArray();
        }
    }

    private String buildPlantUml(final List<ObservationEvent> limited) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("autonumber\n");
        sb.append("skinparam style strictuml\n");

        for (ObservationEvent event : limited) {
            String s = sanitize(event.getSender(), MAX_SENDER);
            String r = sanitize(event.getReceiver(), MAX_SENDER);
            String m = sanitize(event.getMethod(), MAX_METHOD);
            String a = sanitize(event.getAction(), MAX_ACTION);

            sb.append(s).append(" -> ").append(r).append(" : ").append(m);
            if (a != null && !a.isEmpty()) {
                sb.append("\\n(").append(a).append(")");
            }
            sb.append("\n");
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private String sanitize(final String text, final int maxLength) {
        if (text == null) {
            return "unknown";
        }

        String cleaned = text.replaceAll("[^a-zA-Z0-9\\s._\\-:/]", " ")
                             .replace("\n", " ")
                             .trim();

        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength - TRUNC_SUFFIX) + "...";
        }
        return cleaned;
    }
}
