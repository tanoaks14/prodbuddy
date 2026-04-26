package com.prodbuddy.observation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of SequenceLogger that records events in memory.
 */
public class RecordingSequenceLogger implements SequenceLogger {
    
    private final List<ObservationEvent> events = Collections.synchronizedList(new ArrayList<>());
    private final SequenceLogger delegate;
    private final MermaidSequenceGenerator mermaidGen = new MermaidSequenceGenerator();

    public RecordingSequenceLogger(SequenceLogger delegate) {
        this.delegate = delegate;
    }

    @Override
    public void logSequence(ObservationEvent event) {
        events.add(event);
        if (delegate != null) {
            delegate.logSequence(event);
        }
    }

    @Override
    public void logSequence(String sender, String receiver, String method, String action) {
        logSequence(new ObservationEvent(sender, receiver, method, action, java.util.Map.of()));
    }

    @Override
    public void logSequence(String sender, String receiver, String method, String action, java.util.Map<String, String> metadata) {
        logSequence(new ObservationEvent(sender, receiver, method, action, metadata));
    }

    public List<ObservationEvent> getEvents() {
        return new ArrayList<>(events);
    }

    public String toMermaid() {
        return mermaidGen.generate(getEvents());
    }
    
    public void clear() {
        events.clear();
    }
}
