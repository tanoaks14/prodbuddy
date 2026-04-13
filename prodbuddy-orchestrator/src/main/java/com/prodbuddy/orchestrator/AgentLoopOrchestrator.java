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

public final class AgentLoopOrchestrator {

    private final ToolRegistry registry;
    private final ToolRouter router;
    private final LoopConfig config;

    public AgentLoopOrchestrator(ToolRegistry registry, ToolRouter router, LoopConfig config) {
        this.registry = registry;
        this.router = router;
        this.config = config;
    }

    public ToolResponse run(ToolRequest initialRequest, ToolContext context) {
        Instant started = Instant.now();
        ToolRequest request = initialRequest;

        for (int iteration = 1; iteration <= config.maxIterations(); iteration++) {
            if (Instant.now().isAfter(started.plus(config.totalTimeout()))) {
                return ToolResponse.failure("LOOP_TIMEOUT", "total loop timeout exceeded");
            }

            Optional<String> targetTool = router.route(request);
            if (targetTool.isEmpty()) {
                return ToolResponse.failure("ROUTING_FAILED", "no tool route for request intent");
            }

            Optional<Tool> tool = registry.find(targetTool.get());
            if (tool.isEmpty()) {
                return ToolResponse.failure("TOOL_NOT_FOUND", "tool not registered: " + targetTool.get());
            }

            ToolResponse response = tool.get().execute(request, context);
            if (!response.success()) {
                return response;
            }

            // V1 stop condition: single-step success. Multi-step plans can be layered later.
            return ToolResponse.ok(Map.of("iteration", iteration, "tool", targetTool.get(), "result", response.data()));
        }

        return ToolResponse.failure("MAX_ITERATIONS", "max iterations exceeded");
    }
}
