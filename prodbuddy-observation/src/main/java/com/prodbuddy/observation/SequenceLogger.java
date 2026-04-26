package com.prodbuddy.observation;

import java.util.Map;

/**
 * Interface defining the capability to trace events.
 * Adheres to Interface Segregation Principle.
 */
public interface SequenceLogger {
    /**
     * Logs a formatted sequence event.
     * @param event the structured observation event.
     */
    void logSequence(ObservationEvent event);

    /**
     * Helper to directly log a sequence trace.
     * @param sender source of the call
     * @param receiver destination of the call
     * @param method the method name or intent
     * @param action brief description of action
     */
    void logSequence(String sender, String receiver,
                    String method, String action);

    /**
     * Helper to directly log a sequence trace with metadata.
     * @param sender source of the call
     * @param receiver destination of the call
     * @param method the method name or intent
     * @param action brief description of action
     * @param metadata rendering hints and details
     */
    void logSequence(String sender, String receiver,
                    String method, String action,
                    Map<String, String> metadata);
}
