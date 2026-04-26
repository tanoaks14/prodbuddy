package com.prodbuddy.tools.newrelic;

import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class NrqlQueryValidatorTest {

    @Test
    void shouldRejectUnsupportedMetric() {
        NrqlQueryValidator validator = new NrqlQueryValidator(NrqlGuardrails.defaults());
        NrqlQueryRequest request = new NrqlQueryRequest("cpu", Map.of(), 5, 100, "");

        ToolResponse response = validator.validate(request);

        Assertions.assertFalse(response.success());
    }

    @Test
    void shouldAcceptSafeRequest() {
        NrqlQueryValidator validator = new NrqlQueryValidator(NrqlGuardrails.defaults());
        NrqlQueryRequest request = new NrqlQueryRequest("throughput", Map.of("appName", "api"), 5, 100, "");

        ToolResponse response = validator.validate(request);

        Assertions.assertNull(response);
    }
}
