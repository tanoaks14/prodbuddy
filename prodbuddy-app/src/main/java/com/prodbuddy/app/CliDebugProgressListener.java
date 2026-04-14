package com.prodbuddy.app;

import com.prodbuddy.core.tool.ToolRequest;

final class CliDebugProgressListener implements DebugIssueAgentLoop.DebugProgressListener {

    @Override
    public void onStepStart(String stepName, ToolRequest request, String description) {
        String functionCall = request.intent() + "." + request.operation();
        System.out.println("- " + stepName + ": " + description + " (call: " + functionCall + ")");
    }

    @Override
    public void onStepFinish(String stepName, ToolRequest request, boolean success, String summary) {
        String status = success ? "ok" : "failed";
        System.out.println("  result: " + status + " - " + summary);
    }
}
