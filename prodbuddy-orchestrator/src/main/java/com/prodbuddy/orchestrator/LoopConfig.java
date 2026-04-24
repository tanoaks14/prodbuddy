package com.prodbuddy.orchestrator;

import java.time.Duration;

/**
 * Configuration for the agent loop.
 * @param maxIterations the maximum number of iterations
 * @param perStepTimeout the timeout for each step
 * @param totalTimeout the total timeout for the loop
 */
public record LoopConfig(
        int maxIterations,
        Duration perStepTimeout,
        Duration totalTimeout
) {

    /** Default max iterations. */
    private static final int DEFAULT_MAX_ITERATIONS = 10;
    /** Default per-step timeout. */
    private static final int DEFAULT_STEP_TIMEOUT_SECONDS = 30;
    /** Default total timeout. */
    private static final int DEFAULT_TOTAL_TIMEOUT_MINUTES = 5;

    /**
     * @return default configuration.
     */
    public static LoopConfig defaults() {
        return new LoopConfig(DEFAULT_MAX_ITERATIONS,
                Duration.ofSeconds(DEFAULT_STEP_TIMEOUT_SECONDS),
                Duration.ofMinutes(DEFAULT_TOTAL_TIMEOUT_MINUTES));
    }
}
