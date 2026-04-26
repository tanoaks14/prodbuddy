package com.prodbuddy.recipes;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualRecipeTest {

    @Test
    void testSplunkBrowserMimicResolution() throws Exception {
        RecipeLoader loader = new RecipeLoader();
        RecipeDefinition recipe = loader.load(Path.of("d:/apps/prodbuddy/recipes/splunk-browser-mimic.md"));
        
        ToolContext context = new ToolContext("test-req", Map.of(
                "SPLUNK_BASE_URL", "https://splunk.test",
                "SPLUNK_USERNAME", "admin",
                "SPLUNK_FORM_KEY", "form-123"
        ), null);

        RecipeToolExecutor mockExecutor = (request, ctx) -> {
            String op = request.operation();
            if ("login".equals(op)) {
                return ToolResponse.ok(Map.of(
                        "sessionKey", "key-abc",
                        "cookie", "splunkd_8089=key-abc"
                ));
            }
            if ("search".equals(op)) {
                // Check if variables resolved in path and headers
                String path = String.valueOf(request.payload().get("path"));
                if (path.contains("${")) {
                    return ToolResponse.failure("VAR_NOT_RESOLVED", "Path contains unresolved vars: " + path);
                }
                Map<String, Object> headers = (Map<String, Object>) request.payload().get("headers");
                if (headers != null && String.valueOf(headers.get("x-splunk-form-key")).contains("${")) {
                    return ToolResponse.failure("VAR_NOT_RESOLVED", "Header contains unresolved vars");
                }
                return ToolResponse.ok(Map.of("sid", "job-123"));
            }
            return ToolResponse.ok(Map.of("status", 200, "body", "dummy results"));
        };

        RecipeRunner runner = new RecipeRunner();
        RecipeRunResult result = runner.run(recipe, "", context, mockExecutor);

        for (RecipeStepResult step : result.stepResults()) {
            assertTrue(step.response().success(), "Step failed: " + step.stepName() + " - " + step.response().errors());
        }
    }
}
