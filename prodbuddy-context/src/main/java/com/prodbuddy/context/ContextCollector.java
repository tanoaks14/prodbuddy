package com.prodbuddy.context;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.recipes.RecipeToolExecutor;

import java.time.Instant;

/**
 * A transparent {@link RecipeToolExecutor} decorator that records every
 * tool invocation — request payload, response data, timestamp and
 * duration — into a {@link ConversationContext} while delegating
 * actual execution to the wrapped executor.
 *
 * <p>Usage:
 * <pre>
 *   ConversationContext ctx = new ConversationContext(sessionId);
 *   ContextCollector collector = new ContextCollector(orchestrator::run, ctx);
 *   new RecipeRunner().run(recipe, toolContext, collector);
 *   // ctx now has all invocations
 * </pre>
 */
public final class ContextCollector implements RecipeToolExecutor {

    private final RecipeToolExecutor delegate;
    private final ConversationContext context;

    /**
     * Creates a collector wrapping the given executor and recording into the
     * given context.
     */
    public ContextCollector(
            RecipeToolExecutor delegate,
            ConversationContext context
    ) {
        this.delegate = delegate;
        this.context = context;
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext toolContext) {
        Instant start = Instant.now();
        ToolResponse response = delegate.execute(request, toolContext);
        long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
        String stepName = request.intent() + "." + request.operation();
        ToolInvocation invocation = new ToolInvocation(
                stepName, request, response, start, durationMs
        );
        context.add(invocation);
        return response;
    }

    /** Returns the context being collected into. */
    public ConversationContext context() {
        return context;
    }
}
