package com.prodbuddy.observation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SLF4J implementation of the SequenceLogger interface.
 * Dependency Inversion friendly.
 */
public class Slf4jSequenceLogger implements SequenceLogger {
    
    private final Logger logger;

    public Slf4jSequenceLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    @Override
    public void logSequence(ObservationEvent event) {
        // Logging at INFO level to ensure it prints for extraction.
        logger.info(event.formatLog());
    }

    @Override
    public void logSequence(String sender, String receiver, String method, String action) {
        logSequence(new ObservationEvent(sender, receiver, method, action, java.util.Map.of()));
    }

    @Override
    public void logSequence(String sender, String receiver, String method, String action, java.util.Map<String, String> metadata) {
        logSequence(new ObservationEvent(sender, receiver, method, action, metadata));
    }
}
