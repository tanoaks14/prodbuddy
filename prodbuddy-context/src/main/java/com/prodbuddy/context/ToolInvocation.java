package com.prodbuddy.context;

import java.time.Instant;
import java.util.Map;

import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

/**
 * Immutable record of a single tool invocation — the full request,
 * the full response, when it happened, and how long it took (ms).
 *
 * <p>Captured automatically by {@link ContextCollector} for every
 * tool call that passes through the recipe executor.
 */
public final class ToolInvocation {

    private final String stepName;
    private final String tool;
    private final String operation;
    private final Map<String, Object> requestPayload;
    private final boolean success;
    private final Map<String, Object> responseData;
    private final String errorSummary;
    private final Instant timestamp;
    private final long durationMs;

    ToolInvocation(
            String stepName,
            ToolRequest request,
            ToolResponse response,
            Instant timestamp,
            long durationMs
    ) {
        this.stepName = stepName;
        this.tool = request.intent();
        this.operation = request.operation();
        this.requestPayload = request.payload();
        this.success = response.success();
        this.responseData = response.data();
        this.errorSummary = response.errors().isEmpty()
                ? null
                : response.errors().get(0).code()
                        + ": " + response.errors().get(0).message();
        this.timestamp = timestamp;
        this.durationMs = durationMs;
    }

    /** The recipe step name (e.g. "get-random-user"). */
    public String stepName() {
        return stepName;
    }

    /** The tool that was invoked (e.g. "http", "elasticsearch"). */
    public String tool() {
        return tool;
    }

    /** The operation performed (e.g. "get", "search"). */
    public String operation() {
        return operation;
    }

    /** The full request payload sent to the tool. */
    public Map<String, Object> requestPayload() {
        return requestPayload;
    }

    /** Whether the tool call succeeded. */
    public boolean success() {
        return success;
    }

    /** The full response data map returned by the tool. */
    public Map<String, Object> responseData() {
        return responseData;
    }

    /** First error code+message if the call failed, otherwise null. */
    public String errorSummary() {
        return errorSummary;
    }

    /** When this invocation was recorded. */
    public Instant timestamp() {
        return timestamp;
    }

    /** How long the tool call took in milliseconds. */
    public long durationMs() {
        return durationMs;
    }
}
