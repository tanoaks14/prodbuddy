package com.prodbuddy.recipes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

/**
 * Executes a recipe step-by-step, resolving variables before each step and
 * collecting results for downstream cross-step data references.
 *
 * <p>
 * Continues on per-step failure — all steps are attempted regardless.
 */
public final class RecipeRunner {

    private final RecipeVarResolver resolver = new RecipeVarResolver();
    private final LogicEvaluator evaluator = new LogicEvaluator();
    private final SequenceLogger seqLog = new Slf4jSequenceLogger(RecipeRunner.class);

    public RecipeRunResult run(RecipeDefinition recipe, ToolContext context, RecipeToolExecutor executor) {
        seqLog.logSequence("RecipeCliHandler", "RecipeRunner", "run", "Running recipe: " + recipe.name());
        Map<String, ToolResponse> stepData = new LinkedHashMap<>();
        List<RecipeStepResult> results = new ArrayList<>();
        for (RecipeStep step : recipe.steps()) {
            seqLog.logSequence("RecipeRunner", "Orchestrator", "runStep", "Step: " + step.name());

            if (!step.foreach().isEmpty()) {
                boolean cont = executeLoop(step, context, executor, stepData, results);
                if (!cont) break;
                continue;
            }

            if (!shouldRun(step, context, stepData, Map.of())) {
                seqLog.logSequence("RecipeRunner", "Orchestrator", "skipStep", "Skipping step " + step.name() + " due to condition");
                continue;
            }

            RecipeStepResult result = runStep(step, context, executor, stepData, Map.of());
            results.add(result);
            seqLog.logSequence("Orchestrator", "RecipeRunner", "runStep", "Result: " + result.response().success());
            stepData.put(step.name(), result.response());
        }
        seqLog.logSequence("RecipeRunner", "RecipeCliHandler", "run", "Recipe complete: " + results.size() + " steps");
        return new RecipeRunResult(recipe.name(), results);
    }

    private RecipeStepResult runStep(
            RecipeStep step,
            ToolContext context,
            RecipeToolExecutor executor,
            Map<String, ToolResponse> stepData,
            Map<String, Object> localVars
    ) {
        Map<String, Object> resolved = resolver.resolveAll(step.rawParams(), context, stepData, localVars);
        String tool = resolver.resolve(step.tool(), context, stepData, localVars);
        String operation = resolver.resolve(step.operation(), context, stepData, localVars);
        ToolRequest request = buildRequest(tool, operation, resolved);
        ToolResponse response = safeExecute(executor, request, context);
        return new RecipeStepResult(step.name(), tool, operation, resolved, response);
    }

    private boolean executeLoop(
            RecipeStep loopStep,
            ToolContext context,
            RecipeToolExecutor executor,
            Map<String, ToolResponse> stepData,
            List<RecipeStepResult> results
    ) {
        String rawItems = resolver.resolve(loopStep.foreach(), context, stepData);
        List<Object> items = parseList(rawItems);
        String as = loopStep.as().isEmpty() ? "item" : loopStep.as();

        int i = 0;
        for (Object item : items) {
            Map<String, Object> localVars = Map.of(as, item);
            for (RecipeStep nested : loopStep.nestedSteps()) {
                if (!shouldRun(nested, context, stepData, localVars)) continue;

                RecipeStepResult res = runStep(nested, context, executor, stepData, localVars);
                results.add(res);

                // Index-based result aggregation for safety
                String storageKey = nested.name() + "_" + i;
                stepData.put(storageKey, res.response());

                if (loopStep.stopOnFailure() && !res.response().success()) {
                    seqLog.logSequence("RecipeRunner", "Orchestrator", "loopAbort", "Aborting loop " + loopStep.name() + " due to failure in " + nested.name());
                    return false;
                }
            }
            i++;
        }
        return true;
    }

    private List<Object> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.List.of();
        }
        // Support comma separated strings as a fallback for loops
        return java.util.Arrays.asList((Object[]) raw.split(","));
    }

    private boolean shouldRun(RecipeStep step, ToolContext context, Map<String, ToolResponse> stepData, Map<String, Object> localVars) {
        String rawCondition = step.condition();
        if (rawCondition == null || rawCondition.isBlank()) {
            return true;
        }
        String resolved = resolver.resolve(rawCondition, context, stepData, localVars);
        return evaluator.evaluate(resolved);
    }

    private ToolRequest buildRequest(String tool, String operation, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(params);
        return new ToolRequest(tool, operation, payload);
    }

    private ToolResponse safeExecute(RecipeToolExecutor executor, ToolRequest request, ToolContext context) {
        try {
            return executor.execute(request, context);
        } catch (Exception ex) {
            ex.printStackTrace();
            String message = ex.getMessage();
            if (message == null || message.isBlank()) {
                message = ex.getClass().getSimpleName();
            }
            return ToolResponse.failure("RECIPE_STEP_EXCEPTION", message);
        }
    }
}
