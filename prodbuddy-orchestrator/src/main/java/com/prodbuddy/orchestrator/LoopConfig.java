package com.prodbuddy.orchestrator;

import java.time.Duration;

public record LoopConfig(int maxIterations, Duration perStepTimeout, Duration totalTimeout) {

    public static LoopConfig defaults() {
        return new LoopConfig(10, Duration.ofSeconds(30), Duration.ofMinutes(5));
    }
}
