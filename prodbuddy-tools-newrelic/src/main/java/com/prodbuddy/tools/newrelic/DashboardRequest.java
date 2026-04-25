package com.prodbuddy.tools.newrelic;

import java.util.Map;

public record DashboardRequest(
        String guid,
        String name,
        String compareWith,
        String pageGuid,
        int duration,
        Map<String, String> variables
) {
    public DashboardRequest {
        guid = guid == null ? "" : guid;
        name = name == null ? "" : name;
        compareWith = compareWith == null ? "" : compareWith;
        pageGuid = pageGuid == null ? "" : pageGuid;
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public DashboardRequest(String guid, String name, String compareWith, String pageGuid, int duration) {
        this(guid, name, compareWith, pageGuid, duration, Map.of());
    }

    public DashboardRequest(String guid, String name, String compareWith, String pageGuid) {
        this(guid, name, compareWith, pageGuid, 0, Map.of());
    }

    public DashboardRequest(String guid, String name, String compareWith) {
        this(guid, name, compareWith, "", 0, Map.of());
    }
}
