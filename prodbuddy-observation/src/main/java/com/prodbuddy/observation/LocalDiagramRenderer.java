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
    public byte[] renderSequence(List<ObservationEvent> events) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("autonumber\n");
        sb.append("skinparam style strictuml\n");
        sb.append("skinparam SequenceMessageAlignment center\n");

        for (ObservationEvent event : events) {
            String s = sanitize(event.getSender());
            String r = sanitize(event.getReceiver());
            String m = sanitize(event.getMethod());
            String a = sanitize(event.getAction());

            sb.append(s).append(" -> ").append(r).append(" : ").append(m);
            if (a != null && !a.isEmpty()) {
                sb.append("\\n(").append(a).append(")");
            }
            sb.append("\n");
        }

        sb.append("@enduml");

        SourceStringReader reader = new SourceStringReader(sb.toString());
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            reader.generateImage(os);
            return os.toByteArray();
        }
    }

    private String sanitize(String text) {
        if (text == null) return "unknown";
        return text.replace("\"", "").replace("(", "").replace(")", "").replace("\n", " ").trim();
    }
}
