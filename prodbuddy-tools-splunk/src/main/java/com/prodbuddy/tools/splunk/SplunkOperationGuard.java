package com.prodbuddy.tools.splunk;

import java.util.Set;

/** Guard for Splunk operations. */
public final class SplunkOperationGuard {

    /** Allowed operations. */
    private static final Set<String> ALLOWED_OPERATIONS = Set.of(
            "search", "oneshot", "jobs", "results", "login");

    /**
     * Checks if an operation is allowed.
     * @param operation Operation name.
     * @return true if allowed.
     */
    public boolean isAllowed(final String operation) {
        return ALLOWED_OPERATIONS.contains(operation.toLowerCase());
    }
}
