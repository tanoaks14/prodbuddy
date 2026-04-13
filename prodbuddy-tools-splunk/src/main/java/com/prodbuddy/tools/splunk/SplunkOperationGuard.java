package com.prodbuddy.tools.splunk;

import java.util.Set;

public final class SplunkOperationGuard {

    private static final Set<String> ALLOWED_OPERATIONS = Set.of("search", "oneshot", "jobs", "results");

    public boolean isAllowed(String operation) {
        return ALLOWED_OPERATIONS.contains(operation.toLowerCase());
    }
}
