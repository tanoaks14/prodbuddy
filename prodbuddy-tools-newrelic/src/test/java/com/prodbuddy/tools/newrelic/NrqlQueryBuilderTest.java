package com.prodbuddy.tools.newrelic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class NrqlQueryBuilderTest {

    @Test
    void shouldBuildLatencyQuery() {
        NrqlQueryBuilder builder = new NrqlQueryBuilder();
        NrqlQueryRequest request = new NrqlQueryRequest("latency", Map.of("appName", "checkout"), 10, 100, "");

        String nrql = builder.build(request);

        Assertions.assertTrue(nrql.contains("SELECT average(duration)"));
        Assertions.assertTrue(nrql.contains("FROM Transaction"));
        Assertions.assertTrue(nrql.contains("appName = 'checkout'"));
        Assertions.assertTrue(nrql.contains("SINCE 10 minutes ago"));
    }
}
