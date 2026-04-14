package com.prodbuddy.app;

import com.prodbuddy.core.tool.ToolRequest;

final class CliDebugProgressListener implements DebugIssueAgentLoop.DebugProgressListener {

    @Override
    public void onStepStart(String stepName, ToolRequest request, String description) {
        String functionCall = request.intent() + "." + request.operation();
        System.out.println("- " + stepName + ": " + description + " (call: " + functionCall + ")");
        String payloadPreview = payloadPreview(request);
        if (!payloadPreview.isBlank()) {
            System.out.println("  input: " + payloadPreview);
        }
    }

    @Override
    public void onStepFinish(String stepName, ToolRequest request, boolean success, String summary) {
        String status = success ? "ok" : "failed";
        System.out.println("  result: " + status + " - " + summary);
    }

    private String payloadPreview(ToolRequest request) {
        Object index = request.payload().get("index");
        Object terms = request.payload().get("terms");
        Object queryString = request.payload().get("queryString");
        Object symptom = request.payload().get("symptom");
        Object authMode = request.payload().get("authMode");
        if (index != null || terms != null || queryString != null || symptom != null || authMode != null) {
            StringBuilder builder = new StringBuilder();
            append(builder, "index", index);
            append(builder, "terms", terms);
            append(builder, "query", queryString);
            append(builder, "symptom", symptom);
            append(builder, "authMode", authMode);
            return builder.toString();
        }
        return "";
    }

    private void append(StringBuilder builder, String key, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(key).append("=").append(value);
    }
}
