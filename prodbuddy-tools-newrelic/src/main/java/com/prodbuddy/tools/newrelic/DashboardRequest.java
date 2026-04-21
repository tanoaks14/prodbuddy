package com.prodbuddy.tools.newrelic;

public record DashboardRequest(
        String guid,
        String name
) {
    public DashboardRequest {
        guid = guid == null ? "" : guid;
        name = name == null ? "" : name;
    }
}
