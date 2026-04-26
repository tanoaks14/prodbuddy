package com.prodbuddy.orchestrator;

import com.prodbuddy.core.tool.*;
import com.prodbuddy.observation.SequenceLogger;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public final class AgentLoopOrchestrator {

    /** Tool registry. */
    private final ToolRegistry registry;
    /** Tool router. */
    private final ToolRouter router;
    /** Loop configuration. */
    private final LoopConfig config;
    /** Sequence logger. */
    private final SequenceLogger seqLog;

    /**
     * Constructs a new orchestrator.
     * @param theRegistry the tool registry
     * @param theRouter the tool router
     * @param theConfig the loop configuration
     */
    public AgentLoopOrchestrator(
            final ToolRegistry theRegistry,
            final ToolRouter theRouter,
            final LoopConfig theConfig
    ) {
        this.registry = theRegistry;
        this.router = theRouter;
        this.config = theConfig;
        this.seqLog = com.prodbuddy.observation.ObservationContext.getLogger();
    }

    /** @return the tool registry. */
    public ToolRegistry registry() {
        return registry;
    }

    /**
     * Runs the orchestration loop.
     * @param initialRequest the initial request
     * @param context the tool context
     * @return the tool response
     */
    public ToolResponse run(
            final ToolRequest initialRequest,
            final ToolContext context
    ) {
        seqLog.logSequence("Client", "AgentLoopOrchestrator",
                "run", "Started Orchestration");
        Instant started = Instant.now();

        for (int iteration = 1; iteration <= config.maxIterations();
             iteration++) {
            if (Instant.now().isAfter(started.plus(config.totalTimeout()))) {
                seqLog.logSequence("AgentLoopOrchestrator", "Client",
                        "run", "Timeout");
                return ToolResponse.failure("LOOP_TIMEOUT",
                        "total loop timeout exceeded");
            }
            ToolResponse resp = executeIteration(initialRequest, context,
                    iteration);
            if (resp != null) {
                return resp;
            }
        }

        seqLog.logSequence("AgentLoopOrchestrator", "Client", "run",
                "Max iterations exceeded");
        return ToolResponse.failure("MAX_ITERATIONS",
                "max iterations exceeded");
    }

    private ToolResponse executeIteration(
            final ToolRequest request,
            final ToolContext ctx,
            final int iter
    ) {
        seqLog.logSequence("AgentLoopOrchestrator", "ToolRouter",
                "route", "Evaluating request");
        Optional<String> target = router.route(request);
        if (target.isEmpty()) {
            return ToolResponse.failure("ROUTING_FAILED",
                    "no tool route for request intent");
        }

        Optional<Tool> tool = registry.find(target.get());
        if (tool.isEmpty()) {
            return ToolResponse.failure("TOOL_NOT_FOUND",
                    "tool not registered: " + target.get());
        }

        return invokeTool(tool.get(), target.get(), request, ctx, iter);
    }

    private ToolResponse invokeTool(
            final Tool tool,
            final String target,
            final ToolRequest request,
            final ToolContext ctx,
            final int iter
    ) {
        String capitalizedTarget = target.substring(0, 1).toUpperCase() + target.substring(1);
        seqLog.logSequence("AgentLoopOrchestrator", capitalizedTarget,
                "execute", "Executing tool");
        ToolResponse response = tool.execute(request, ctx);
        seqLog.logSequence(capitalizedTarget, "AgentLoopOrchestrator",
                "execute", "Success: " + response.success());

        if (!response.success()) {
            return response;
        }

        seqLog.logSequence("AgentLoopOrchestrator", "Client", "run",
                "Successfully finished");
        return ToolResponse.ok(Map.of("iteration", iter,
                "tool", target, "result", response.data()));
    }
}
