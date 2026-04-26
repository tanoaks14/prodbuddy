package com.prodbuddy.observation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * SLF4J implementation of the SequenceLogger interface.
 */
public final class Slf4jSequenceLogger implements SequenceLogger {

    /** Internal logger. */
    private final Logger logger;

    /**
     * Constructor.
     * @param clazz the class to log for.
     */
    public Slf4jSequenceLogger(final Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    @Override
    public void logSequence(final ObservationEvent event) {
        // Logging at INFO level to ensure it prints for extraction.
        logger.info(event.formatLog());
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
}
