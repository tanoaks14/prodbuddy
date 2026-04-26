package com.prodbuddy.observation;

import net.sourceforge.plantuml.SourceStringReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Native Java renderer using PlantUML. No external API calls.
 */
public final class LocalDiagramRenderer {

    /**
     * Renders a list of events to a PNG using PlantUML.
     * @param events The events to render
     * @return PNG bytes
     * @throws IOException if rendering fails
     */
    public byte[] renderSequence(final List<ObservationEvent> events) throws IOException {
        if (events == null || events.isEmpty()) {
            return new byte[0];
        }

        List<ObservationEvent> limited = events.size() > 50 
            ? events.subList(events.size() - 50, events.size()) : events;

        String plantUml = buildPlantUml(limited);
        
        SourceStringReader reader = new SourceStringReader(plantUml);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            reader.generateImage(os);
            return os.toByteArray();
        }
    }

    private String buildPlantUml(List<ObservationEvent> limited) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("autonumber\n");
        sb.append("skinparam style strictuml\n");

        for (ObservationEvent event : limited) {
            String s = sanitize(event.getSender(), 30);
            String r = sanitize(event.getReceiver(), 30);
            String m = sanitize(event.getMethod(), 50);
            String a = sanitize(event.getAction(), 100);

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
        if (text == null) return "unknown";
        
        // Remove characters that break PlantUML syntax
        String cleaned = text.replaceAll("[^a-zA-Z0-9\\s._\\-:/]", " ")
                             .replace("\n", " ")
                             .trim();
                             
        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength - 3) + "...";
        }
        return cleaned;
    }
}
