package com.prodbuddy.observation;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Global singleton for accessing the observation system.
 */
public final class ObservationContext {

    /** The global logger instance. */
    private static final AtomicReference<SequenceLogger> LOGGER =
        new AtomicReference<>(new Slf4jSequenceLogger(
                ObservationContext.class));

    /** Private constructor. */
    private ObservationContext() { }

    /**
     * Sets the global logger.
     * @param logger the new logger instance.
     */
    public static void setLogger(final SequenceLogger logger) {
        LOGGER.set(logger);
    }

    /** @return the current global logger. */
    public static SequenceLogger getLogger() {
        return LOGGER.get();
    }

    /**
     * Convenience method for logging.
     * @param sender source
     * @param receiver destination
     * @param method method
     * @param action action
     */
    public static void log(final String sender, final String receiver,
                           final String method, final String action) {
        LOGGER.get().logSequence(sender, receiver, method, action);
    }
}
