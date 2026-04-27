package com.prodbuddy.recipes;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.observation.SequenceLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final SequenceLogger seqLog =
            com.prodbuddy.observation.ObservationContext.getLogger();
    /** Registry of available recipes. */
    private RecipeRegistry registry;

    /**
     * Executes the given recipe.
     * @param recipe the recipe definition
     * @param fullRecipeContent the raw content
     * @param context the tool context
     * @param executor the tool executor
     * @return the run result
     */
    public RecipeRunResult run(
            final RecipeDefinition recipe,
            final String fullRecipeContent,
            final ToolContext context,
            final RecipeToolExecutor executor
    ) {
        this.registry = RecipeRegistry.loadFrom(java.nio.file.Path.of(
                context.envOrDefault("RECIPES_DIR", "recipes")));
        seqLog.logSequence("RecipeCliHandler", "RecipeRunner", "run",
                "🚀 Running recipe: " + recipe.name());
        Map<String, ToolResponse> stepData = new LinkedHashMap<>();
        List<RecipeStepResult> results = new ArrayList<>();
        for (RecipeStep step : recipe.steps()) {
            if (!processSingleStep(step, fullRecipeContent, context,
                    executor, stepData, results)) {
                break;
            }
        }
        seqLog.logSequence("RecipeRunner", "RecipeCliHandler", "run",
                "🏁 Recipe complete");
        return new RecipeRunResult(recipe.name(), results);
    }

    private boolean processSingleStep(
            final RecipeStep step,
            final String fullRecipeContent,
            final ToolContext context,
            final RecipeToolExecutor executor,
            final Map<String, ToolResponse> stepData,
            final List<RecipeStepResult> results
    ) {
        seqLog.logSequence("RecipeRunner", "Orchestrator", "runStep",
                "⚡ Step: " + step.name());
        if (!step.foreach().isEmpty()) {
            return executeLoop(step, fullRecipeContent, context,
                    executor, stepData, results);
        }
        if (!shouldRun(step, context, stepData, Map.of())) {
            seqLog.logSequence("RecipeRunner", "Orchestrator", "skipStep",
                    "⏭️ Skipping step " + step.name());
            return true;
        }
        RecipeStepResult result = runStep(step, context, executor, stepData,
                Map.of(), fullRecipeContent);
        results.add(result);
        seqLog.logSequence("Orchestrator", "RecipeRunner", "runStep",
                "✅ Result: " + result.response().success());
        stepData.put(step.name(), result.response());
        return true;
    }

    private RecipeStepResult runStep(
            final RecipeStep step,
            final ToolContext context,
            final RecipeToolExecutor executor,
            final Map<String, ToolResponse> stepData,
            final Map<String, Object> localVars,
            final String fullRecipeContent
    ) {
        Map<String, Object> mutableLocal = new java.util.HashMap<>(localVars);
        mutableLocal.put("system.current_recipe", fullRecipeContent);
        mutableLocal.put("system.current_step_name", step.name());

        Map<String, Object> resolved = resolver.resolveAll(step.rawParams(),
                context, stepData, mutableLocal);
        String tool = String.valueOf(resolver.resolve(step.tool(),
                context, stepData, mutableLocal));
        String operation = String.valueOf(resolver.resolve(step.operation(),
                context, stepData, mutableLocal));

        if ("recipe".equals(tool) && "run".equals(operation)) {
            return runSubRecipe(step, resolved, context, executor,
                    stepData, mutableLocal);
        }

        ToolRequest request = buildRequest(tool, operation, resolved);
        ToolResponse response = safeExecute(executor, request, context);
        return new RecipeStepResult(step.name(), tool, operation, resolved,
                response);
    }

    private RecipeStepResult runSubRecipe(
            final RecipeStep step,
            final Map<String, Object> resolved,
            final ToolContext context,
            final RecipeToolExecutor executor,
            final Map<String, ToolResponse> stepData,
            final Map<String, Object> localVars
    ) {
        String subName = String.valueOf(resolved.get("name"));
        RecipeDefinition sub = registry.findByName(subName);
        if (sub == null) {
            return new RecipeStepResult(step.name(), "recipe", "run", resolved,
                    ToolResponse.failure("RECIPE_NOT_FOUND", subName));
        }

        seqLog.logSequence("RecipeRunner", "SubRecipe", "run",
                "Entering: " + subName);

        // IMPROVEMENT: Parameters passed to 'recipe.run' are available as
        // local variables in the sub-recipe
        Map<String, Object> subLocalVars = new java.util.HashMap<>(localVars);
        subLocalVars.putAll(resolved);

        List<RecipeStepResult> subResults = executeSubSteps(sub, context,
                executor, stepData, subLocalVars);

        seqLog.logSequence("SubRecipe", "RecipeRunner", "run",
                "Exited: " + subName);

        return new RecipeStepResult(step.name(), "recipe", "run", resolved,
                ToolResponse.ok(Map.of("sub_recipe", subName,
                        "steps_executed", subResults.size())));
    }

    private List<RecipeStepResult> executeSubSteps(
            final RecipeDefinition sub,
            final ToolContext context,
            final RecipeToolExecutor executor,
            final Map<String, ToolResponse> stepData,
            final Map<String, Object> localVars
    ) {
        List<RecipeStepResult> subResults = new ArrayList<>();
        for (RecipeStep subStep : sub.steps()) {
            if (shouldRun(subStep, context, stepData, localVars)) {
                RecipeStepResult res = runStep(subStep, context, executor,
                        stepData, localVars, "");
                subResults.add(res);
                stepData.put(subStep.name(), res.response());
            }
        }
        return subResults;
    }

    private boolean executeLoop(
            final RecipeStep loopStep,
            final String fullRecipeContent,
            final ToolContext context,
            final RecipeToolExecutor executor,
            final Map<String, ToolResponse> stepData,
            final List<RecipeStepResult> results
    ) {
        Object res = resolver.resolve(loopStep.foreach(), context, stepData,
                Map.of());
        List<Object> items;
        if (res instanceof List list) {
            items = list;
        } else {
            items = parseList(String.valueOf(res));
        }
        String as = loopStep.as().isEmpty() ? "item" : loopStep.as();

        int i = 0;
        for (Object item : items) {
            if (!executeIteration(loopStep, fullRecipeContent, context,
                    executor, stepData, results, item, i)) {
                return false;
            }
            i++;
        }
        return true;
    }

    private boolean executeIteration(
            final RecipeStep loopStep,
            final String fullRecipeContent,
            final ToolContext context,
            final RecipeToolExecutor executor,
            final Map<String, ToolResponse> stepData,
            final List<RecipeStepResult> results,
            final Object item,
            final int index
    ) {
        String as = loopStep.as().isEmpty() ? "item" : loopStep.as();
        Map<String, Object> localVars = Map.of(as, item);
        for (RecipeStep nested : loopStep.nestedSteps()) {
            if (!shouldRun(nested, context, stepData, localVars)) {
                continue;
            }
            RecipeStepResult loopRes = runStep(nested, context, executor,
                    stepData, localVars, fullRecipeContent);
            results.add(loopRes);
            stepData.put(nested.name() + "_" + index, loopRes.response());

            if (loopStep.stopOnFailure() && !loopRes.response().success()) {
                seqLog.logSequence("RecipeRunner", "Orchestrator",
                        "loopAbort", "Aborting loop " + loopStep.name()
                                + " due to failure in " + nested.name());
                return false;
            }
        }
        return true;
    }

    private List<Object> parseList(final String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.List.of();
        }
        // Support comma separated strings as a fallback for loops
        return java.util.Arrays.asList((Object[]) raw.split(","));
    }

    private boolean shouldRun(final RecipeStep step,
                              final ToolContext context,
                              final Map<String, ToolResponse> stepData,
                              final Map<String, Object> localVars) {
        String rawCondition = step.condition();
        if (rawCondition == null || rawCondition.isBlank()) {
            return true;
        }
        String res = String.valueOf(resolver.resolve(rawCondition,
                context, stepData, localVars));
        return evaluator.evaluate(res);
    }

    private ToolRequest buildRequest(final String tool, final String operation,
                                     final Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(params);
        return new ToolRequest(tool, operation, payload);
    }

    private ToolResponse safeExecute(final RecipeToolExecutor executor,
                                     final ToolRequest request,
                                     final ToolContext context) {
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
