package com.prodbuddy.tools.newrelic;

public record DashboardRequest(
        String guid,
        String name,
        String compareWith
) {
    public DashboardRequest {
        guid = guid == null ? "" : guid;
        name = name == null ? "" : name;
        compareWith = compareWith == null ? "" : compareWith;
    }
}
