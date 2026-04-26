package com.prodbuddy.app;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class DebugIssueAgentLoopTest {

    @Test
    void shouldReturnHealthyWhenAllStepsSucceed() {
        DebugIssueAgentLoop loop = new DebugIssueAgentLoop((request, context) -> ToolResponse.ok(Map.of("ok", true)));

        ToolResponse response = loop.run("payment timeout", new ToolContext("req-1", Map.of(), null));

        Assertions.assertTrue(response.success());
        Assertions.assertEquals("healthy", response.data().get("status"));
        Assertions.assertEquals(7, ((Number) response.data().get("successfulSteps")).intValue());
        Assertions.assertEquals(0, ((Number) response.data().get("failedSteps")).intValue());
    }

    @Test
    void shouldContinueCollectingWhenSomeStepsFail() {
        DebugIssueAgentLoop loop = new DebugIssueAgentLoop(DebugIssueAgentLoopTest::executeWithOneFailure);

        ToolResponse response = loop.run("payment timeout", new ToolContext("req-2", Map.of(), null));

        Assertions.assertTrue(response.success());
        Assertions.assertEquals("attention_required", response.data().get("status"));
        Assertions.assertEquals(6, ((Number) response.data().get("successfulSteps")).intValue());
        Assertions.assertEquals(1, ((Number) response.data().get("failedSteps")).intValue());
    }

    private static ToolResponse executeWithOneFailure(ToolRequest request, ToolContext context) {
        context.envOrDefault("DEBUG_WINDOW_MINUTES", "15");
        if ("newrelic".equalsIgnoreCase(request.intent())) {
            return ToolResponse.failure("NEWRELIC_CONFIG", "missing key");
        }
        return ToolResponse.ok(Map.of("intent", request.intent()));
    }
}
