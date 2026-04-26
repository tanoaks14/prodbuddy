package com.prodbuddy.observation;

import java.util.Collections;
import java.util.Map;

/**
 * Data object representing a sequenced event for logging.
 */
public final class ObservationEvent {
    /** Source of the call. */
    private final String sender;
    /** Destination of the call. */
    private final String receiver;
    /** Method or intent name. */
    private final String method;
    /** Action description. */
    private final String action;
    /** Rendering hints. */
    private final Map<String, String> metadata;

    /**
     * Constructor.
     * @param s sender
     * @param r receiver
     * @param m method
     * @param a action
     */
    public ObservationEvent(final String s, final String r,
                            final String m, final String a) {
        this(s, r, m, a, Map.of());
    }

    /**
     * Constructor with metadata.
     * @param s sender
     * @param r receiver
     * @param m method
     * @param a action
     * @param meta metadata
     */
    public ObservationEvent(final String s, final String r,
                            final String m, final String a,
                            final Map<String, String> meta) {
        this.sender = s;
        this.receiver = r;
        this.method = m;
        this.action = a;
        this.metadata = meta == null ? Map.of()
                : Collections.unmodifiableMap(meta);
    }

    /** @return sender. */
    public String getSender() {
        return sender;
    }

    /** @return receiver. */
    public String getReceiver() {
        return receiver;
    }

    /** @return method. */
    public String getMethod() {
        return method;
    }

    /** @return action. */
    public String getAction() {
        return action;
    }

    /** @return metadata. */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Formats the event into a standard log parsable format.
     * @return formatted string
     */
    public String formatLog() {
        return String.format("[SEQUENCE_TRACE] sender=\"%s\" "
                + "receiver=\"%s\" method=\"%s\" action=\"%s\"",
                sender, receiver, method, action);
    }
}
