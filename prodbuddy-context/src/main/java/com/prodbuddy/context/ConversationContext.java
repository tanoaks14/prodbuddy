package com.prodbuddy.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Session-scoped store of all {@link ToolInvocation} records captured
 * during a recipe or debug-loop run.
 *
 * <p>Thread-safe via synchronized access. Designed to be shared across
 * all steps of a single run so the full call-graph is accumulated
 * in chronological order.
 */
public final class ConversationContext {

    private final List<ToolInvocation> invocations = new ArrayList<>();
    private final String sessionId;

    /** Creates a new context for the given session identifier. */
    public ConversationContext(String sessionId) {
        this.sessionId = sessionId;
    }

    /** Adds a completed invocation to the context. */
    synchronized void add(ToolInvocation invocation) {
        invocations.add(invocation);
    }

    /** Returns an unmodifiable snapshot of all invocations in order. */
    public synchronized List<ToolInvocation> invocations() {
        return Collections.unmodifiableList(new ArrayList<>(invocations));
    }

    /** Returns only the successful invocations. */
    public synchronized List<ToolInvocation> successful() {
        return invocations.stream()
                .filter(ToolInvocation::success)
                .toList();
    }

    /** Returns only the failed invocations. */
    public synchronized List<ToolInvocation> failed() {
        return invocations.stream()
                .filter(i -> !i.success())
                .toList();
    }

    /** Returns invocations for a specific tool (e.g. "http", "elasticsearch"). */
    public synchronized List<ToolInvocation> forTool(String tool) {
        return invocations.stream()
                .filter(i -> i.tool().equalsIgnoreCase(tool))
                .toList();
    }

    /** Total number of invocations recorded. */
    public synchronized int size() {
        return invocations.size();
    }

    /** The session identifier this context belongs to. */
    public String sessionId() {
        return sessionId;
    }
}
