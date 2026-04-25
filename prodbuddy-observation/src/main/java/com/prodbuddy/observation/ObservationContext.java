package com.prodbuddy.observation;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Global singleton for accessing the observation system.
 * Allows SPI-loaded tools to contribute to the shared trace.
 */
public final class ObservationContext {

    private static final AtomicReference<SequenceLogger> LOGGER = 
        new AtomicReference<>(new Slf4jSequenceLogger(ObservationContext.class));

    private ObservationContext() {}

    /**
     * Sets the global logger (e.g. to a RecordingSequenceLogger).
     */
    public static void setLogger(SequenceLogger logger) {
        LOGGER.set(logger);
    }

    /**
     * Gets the current global logger.
     */
    public static SequenceLogger getLogger() {
        return LOGGER.get();
    }

    /**
     * Convenience method for logging.
     */
    public static void log(String sender, String receiver, String method, String action) {
        LOGGER.get().logSequence(sender, receiver, method, action);
    }
}
