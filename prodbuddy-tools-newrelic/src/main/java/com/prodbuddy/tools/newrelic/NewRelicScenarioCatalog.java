package com.prodbuddy.tools.newrelic;

import java.util.Map;

public final class NewRelicScenarioCatalog {

    public String queryFor(String scenario) {
        return switch (scenario) {
            case "errors" ->
                "SELECT count(*) FROM TransactionError TIMESERIES 1 minute";
            case "latency" ->
                "SELECT average(duration) FROM Transaction TIMESERIES 1 minute";
            case "throughput" ->
                "SELECT rate(count(*), 1 minute) FROM Transaction TIMESERIES 1 minute";
            default ->
                "SELECT count(*) FROM Transaction TIMESERIES 1 minute";
        };
    }

    public Map<String, Object> supported() {
        return Map.of("scenarios", new String[]{"errors", "latency", "throughput", "default"});
    }
}
