package com.prodbuddy.recipes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

/**
 * Executes a recipe step-by-step, resolving variables before each step and
 * collecting results for downstream cross-step data references.
 *
 * <p>
 * Continues on per-step failure — all steps are attempted regardless.
 */
public final class RecipeRunner {

    private final RecipeVarResolver resolver = new RecipeVarResolver();

    public RecipeRunResult run(RecipeDefinition recipe, ToolContext context, RecipeToolExecutor executor) {
        Map<String, ToolResponse> stepData = new LinkedHashMap<>();
        List<RecipeStepResult> results = new ArrayList<>();
        for (RecipeStep step : recipe.steps()) {
            RecipeStepResult result = runStep(step, context, executor, stepData);
            results.add(result);
            if (result.response().success()) {
                stepData.put(step.name(), result.response());
            }
        }
        return new RecipeRunResult(recipe.name(), results);
    }

    private RecipeStepResult runStep(
            RecipeStep step,
            ToolContext context,
            RecipeToolExecutor executor,
            Map<String, ToolResponse> stepData
    ) {
        Map<String, String> resolved = resolver.resolveAll(step.rawParams(), context, stepData);
        String tool = resolver.resolve(step.tool(), context, stepData);
        String operation = resolver.resolve(step.operation(), context, stepData);
        ToolRequest request = buildRequest(tool, operation, resolved);
        ToolResponse response = safeExecute(executor, request, context);
        return new RecipeStepResult(step.name(), tool, operation, resolved, response);
    }

    private ToolRequest buildRequest(String tool, String operation, Map<String, String> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(params);
        return new ToolRequest(tool, operation, payload);
    }

    private ToolResponse safeExecute(RecipeToolExecutor executor, ToolRequest request, ToolContext context) {
        try {
            return executor.execute(request, context);
        } catch (Exception ex) {
            String message = ex.getMessage();
            if (message == null || message.isBlank()) {
                message = ex.getClass().getSimpleName();
            }
            return ToolResponse.failure("RECIPE_STEP_EXCEPTION", message);
        }
    }
}
