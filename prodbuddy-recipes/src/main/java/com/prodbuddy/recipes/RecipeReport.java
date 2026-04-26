package com.prodbuddy.recipes;

import com.prodbuddy.core.tool.ToolError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeReport {

    private RecipeReport() {
    }

    public static Map<String, Object> summarize(RecipeRunResult result) {
        List<Map<String, Object>> passed = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (RecipeStepResult step : result.stepResults()) {
            if (step.response().success()) {
                passed.add(Map.of(
                        "step", step.stepName(),
                        "call", step.tool() + "." + step.operation()
                ));
            } else {
                failed.add(failureSummary(step));
            }
        }
        return buildSummaryMap(result, passed, failed);
    }

    private static Map<String, Object> buildSummaryMap(
            RecipeRunResult result,
            List<Map<String, Object>> passed,
            List<Map<String, Object>> failed
    ) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("recipe", result.recipeName());
        report.put("totalSteps", result.stepResults().size());
        report.put("passed", passed.size());
        report.put("failed", failed.size());
        report.put("passedSteps", passed);
        report.put("failedSteps", failed);
        return report;
    }

    private static Map<String, Object> failureSummary(RecipeStepResult step) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (ToolError error : step.response().errors()) {
            errors.add(Map.of("code", error.code(), "message", error.message()));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("step", step.stepName());
        summary.put("call", step.tool() + "." + step.operation());
        summary.put("errors", errors);
        return summary;
    }
}
