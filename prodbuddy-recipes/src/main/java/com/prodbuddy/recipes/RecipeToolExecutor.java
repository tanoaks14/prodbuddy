package com.prodbuddy.recipes;

import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

/**
 * Single-method contract for executing a tool step within a recipe.
 * Implementations are provided by callers (e.g. AgentLoopOrchestrator::run).
 */
@FunctionalInterface
public interface RecipeToolExecutor {

    ToolResponse execute(ToolRequest request, ToolContext context);
}
