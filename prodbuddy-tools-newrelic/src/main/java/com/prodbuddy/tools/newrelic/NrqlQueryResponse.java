package com.prodbuddy.tools.newrelic;

import java.util.Collections;
import java.util.Map;

/**
 * Data record for NRQL query response.
 * @param nrql the query string
 * @param status HTTP status code
 * @param body response body
 * @param diagnostics additional diagnostic data
 */
public record NrqlQueryResponse(
        String nrql,
        int status,
        String body,
        Map<String, Object> diagnostics
        ) {

    /** Canonical constructor. */
    public NrqlQueryResponse {
        diagnostics = diagnostics == null ? Map.of()
            : Collections.unmodifiableMap(diagnostics);
    }
}
