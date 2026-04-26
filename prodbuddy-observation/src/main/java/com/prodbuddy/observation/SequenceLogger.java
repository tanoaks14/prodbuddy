package com.prodbuddy.observation;

/**
 * Interface defining the capability to trace events for generating sequence diagrams.
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
     */
    void logSequence(String sender, String receiver, String method, String action);

    /**
     * Helper to directly log a sequence trace with metadata.
     */
    void logSequence(String sender, String receiver, String method, String action, java.util.Map<String, String> metadata);
}
