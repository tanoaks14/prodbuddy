package com.prodbuddy.orchestrator;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.core.tool.ToolRouter;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

public final class AgentLoopOrchestrator {

    private final ToolRegistry registry;
    private final ToolRouter router;
    private final LoopConfig config;
    private final SequenceLogger seqLog;

    public AgentLoopOrchestrator(ToolRegistry registry, ToolRouter router, LoopConfig config) {
        this.registry = registry;
        this.router = router;
        this.config = config;
        this.seqLog = new Slf4jSequenceLogger(AgentLoopOrchestrator.class);
    }

    public ToolRegistry registry() {
        return registry;
    }

    public ToolResponse run(ToolRequest initialRequest, ToolContext context) {
        seqLog.logSequence("Client", "AgentLoopOrchestrator", "run", "Started Orchestration");
        Instant started = Instant.now();

        for (int iteration = 1; iteration <= config.maxIterations(); iteration++) {
            if (Instant.now().isAfter(started.plus(config.totalTimeout()))) {
                seqLog.logSequence("AgentLoopOrchestrator", "Client", "run", "Timeout");
                return ToolResponse.failure("LOOP_TIMEOUT", "total loop timeout exceeded");
            }
            ToolResponse resp = executeIteration(initialRequest, context, iteration);
            if (resp != null) {
                return resp;
            }
        }

        seqLog.logSequence("AgentLoopOrchestrator", "Client", "run", "Max iterations exceeded");
        return ToolResponse.failure("MAX_ITERATIONS", "max iterations exceeded");
    }

    private ToolResponse executeIteration(ToolRequest request, ToolContext ctx, int iter) {
        seqLog.logSequence("AgentLoopOrchestrator", "ToolRouter", "route", "Evaluating request");
        Optional<String> targetTool = router.route(request);
        if (targetTool.isEmpty()) {
            seqLog.logSequence("ToolRouter", "AgentLoopOrchestrator", "route", "Routing failed");
            return ToolResponse.failure("ROUTING_FAILED", "no tool route for request intent");
        }
        seqLog.logSequence("ToolRouter", "AgentLoopOrchestrator", "route", "Target: " + targetTool.get());

        seqLog.logSequence("AgentLoopOrchestrator", "ToolRegistry", "find", "Look up: " + targetTool.get());
        Optional<Tool> tool = registry.find(targetTool.get());
        if (tool.isEmpty()) {
            seqLog.logSequence("ToolRegistry", "AgentLoopOrchestrator", "find", "Not Found");
            return ToolResponse.failure("TOOL_NOT_FOUND", "tool not registered: " + targetTool.get());
        }

        seqLog.logSequence("AgentLoopOrchestrator", targetTool.get(), "execute", "Executing tool");
        ToolResponse response = tool.get().execute(request, ctx);
        seqLog.logSequence(targetTool.get(), "AgentLoopOrchestrator", "execute", "Success: " + response.success());
        
        if (!response.success()) {
            return response;
        }

        seqLog.logSequence("AgentLoopOrchestrator", "Client", "run", "Successfully finished");
        return ToolResponse.ok(Map.of("iteration", iter, "tool", targetTool.get(), "result", response.data()));
    }
}
