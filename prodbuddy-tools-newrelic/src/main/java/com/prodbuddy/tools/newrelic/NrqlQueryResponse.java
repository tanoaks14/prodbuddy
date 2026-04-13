package com.prodbuddy.tools.newrelic;

import java.util.Collections;
import java.util.Map;

public record NrqlQueryResponse(
        String nrql,
        int status,
        String body,
        Map<String, Object> diagnostics
        ) {

    public NrqlQueryResponse    {
        diagnostics = diagnostics == null ? Map.of() : Collections.unmodifiableMap(diagnostics);
    }
}
