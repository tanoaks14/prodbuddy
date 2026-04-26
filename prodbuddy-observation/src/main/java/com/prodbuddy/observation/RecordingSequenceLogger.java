package com.prodbuddy.observation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implementation of SequenceLogger that records events in memory.
 */
public final class RecordingSequenceLogger implements SequenceLogger {

    /** In-memory storage for recorded events. */
    private final List<ObservationEvent> events =
            Collections.synchronizedList(new ArrayList<>());
    /** Delegate logger for actual output. */
    private final SequenceLogger delegate;
    /** Mermaid generator. */
    private final MermaidSequenceGenerator mermaidGen =
            new MermaidSequenceGenerator();

    /**
     * Constructor.
     * @param theDelegate the logger to delegate actual output to.
     */
    public RecordingSequenceLogger(final SequenceLogger theDelegate) {
        this.delegate = theDelegate;
    }

    @Override
    public void logSequence(final ObservationEvent event) {
        events.add(event);
        if (delegate != null) {
            delegate.logSequence(event);
        }
    }

    @Override
    public void logSequence(final String sender, final String receiver,
                            final String method, final String action) {
        logSequence(new ObservationEvent(sender, receiver, method, action,
                    Map.of()));
    }

    @Override
    public void logSequence(final String sender, final String receiver,
                            final String method, final String action,
                            final Map<String, String> metadata) {
        logSequence(new ObservationEvent(sender, receiver, method, action,
                    metadata));
    }

    /**
     * @return the list of recorded events.
     */
    public List<ObservationEvent> getEvents() {
        return new ArrayList<>(events);
    }

    /**
     * @return Mermaid sequence diagram string.
     */
    public String toMermaid() {
        return mermaidGen.generate(getEvents());
    }

    /**
     * Clears the recorded events.
     */
    public void clear() {
        events.clear();
    }
}
