package com.prodbuddy.tools.newrelic;

public record DashboardRequest(
        String guid,
        String name,
        String compareWith,
        String pageGuid,
        int duration
) {
    public DashboardRequest {
        guid = guid == null ? "" : guid;
        name = name == null ? "" : name;
        compareWith = compareWith == null ? "" : compareWith;
        pageGuid = pageGuid == null ? "" : pageGuid;
    }

    public DashboardRequest(String guid, String name, String compareWith, String pageGuid) {
        this(guid, name, compareWith, pageGuid, 0);
    }

    public DashboardRequest(String guid, String name, String compareWith) {
        this(guid, name, compareWith, "", 0);
    }
}
