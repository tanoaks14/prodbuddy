package com.prodbuddy.tools.agent;

import com.prodbuddy.core.agent.AgentConfig;
import com.prodbuddy.core.agent.OllamaAgentClient;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolResponse;
import com.prodbuddy.recipes.RecipeDefinition;
import com.prodbuddy.recipes.RecipeLoader;
import com.prodbuddy.recipes.RecipeStep;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecipeAgentHelper {

    private final OllamaAgentClient client;
    private final AgentConfig config;

    public RecipeAgentHelper(final OllamaAgentClient client,
                             final AgentConfig config) {
        this.client = client;
        this.config = config;
    }

    public ToolResponse generate(final String objective,
                                 final String guide,
                                 final ToolContext context) {
        String prompt = buildPrompt(objective, guide);
        try {
            String recipe = client.generate(prompt, config);
            validate(recipe);
            String path = save(recipe, context);
            return ToolResponse.ok(Map.of("recipe", recipe,
                    "path", path, "status", "generated"));
        } catch (Exception e) {
            return ToolResponse.failure("GENERATION_FAILED", e.getMessage());
        }
    }

    private void validate(final String content) throws Exception {
        java.nio.file.Path temp = java.nio.file.Files
                .createTempFile("gen-recipe-val", ".md");
        try {
            java.nio.file.Files.writeString(temp, content);
            new RecipeLoader().load(temp);
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }

    private String save(final String content, final ToolContext context)
            throws java.io.IOException {
        String dir = context.envOrDefault("RECIPES_DIR", "recipes");
        String fileName = "generated-" + System.currentTimeMillis() + ".md";
        java.nio.file.Path path = java.nio.file.Path.of(dir).resolve(fileName);
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, content);
        return path.toString();
    }

    private String buildPrompt(final String obj, final String guide) {
        return "Goal: " + obj + "\nGuide: " + guide + "\nGenerate recipe.";
    }

    public ToolResponse validateContent(final String content,
                                        final ToolContext context) {
        List<String> errors = new ArrayList<>();
        try {
            java.nio.file.Path temp = java.nio.file.Files
                    .createTempFile("recipe-val", ".md");
            try {
                java.nio.file.Files.writeString(temp, content);
                RecipeDefinition def = new RecipeLoader().load(temp);
                checkSemantic(def, errors, context);
                checkReferences(def, errors);
            } finally {
                java.nio.file.Files.deleteIfExists(temp);
            }
        } catch (Exception e) {
            errors.add("Error: " + e.getMessage());
        }
        return ToolResponse.ok(Map.of("valid", errors.isEmpty(),
                "errors", errors));
    }

    private void checkSemantic(final RecipeDefinition def,
                              final List<String> errors,
                              final ToolContext context) {
        Map<String, Set<String>> toolMap = new HashMap<>();
        context.registry().metadata().forEach(m ->
                toolMap.put(m.name(), m.capabilities()));

        for (RecipeStep step : def.steps()) {
            if (!toolMap.containsKey(step.tool())) {
                errors.add("Unknown tool: " + step.tool());
            } else {
                validateToolSpecifics(step, errors);
            }
        }
    }

    private void validateToolSpecifics(RecipeStep step, List<String> errors) {
        if ("json".equals(step.tool()) && "extract".equals(step.operation())) {
            Object pathsObj = step.rawParams().get("paths");
            if (pathsObj != null && !(pathsObj instanceof Map)) {
                errors.add("Step '" + step.name() + "': json.extract requires 'paths' to be formatted as a Map (key-value pairs) with proper YAML indentation.");
            }
        } else if ("elasticsearch".equals(step.tool())) {
            validateElasticTool(step, errors);
        } else if ("graphql".equals(step.tool())) {
            validateGraphQLTool(step, errors);
        }
    }

    private void validateGraphQLTool(RecipeStep step, List<String> errors) {
        if (!step.rawParams().containsKey("url")) {
            errors.add("Step '" + step.name() + "': graphql tool requires a 'url' parameter.");
        }
        if ("query".equals(step.operation())) {
            if (!step.rawParams().containsKey("query")) {
                errors.add("Step '" + step.name() + "': graphql.query requires a 'query' parameter containing the GraphQL query string.");
            }
            Object vars = step.rawParams().get("variables");
            if (vars != null && !(vars instanceof Map) && !(vars instanceof String && ((String) vars).contains("${"))) {
                errors.add("Step '" + step.name() + "': graphql 'variables' should be a Map of key-value pairs.");
            }
        }
    }

    private void validateElasticTool(RecipeStep step, List<String> errors) {
        if (step.rawParams().containsKey("data") && !step.rawParams().containsKey("body") && !step.rawParams().containsKey("queryDsl")) {
            errors.add("Step '" + step.name() + "': elasticsearch tool uses 'body', 'queryDsl', or 'queryString' for queries, not 'data'. Please rename 'data' to 'body'.");
        }
        if (step.rawParams().containsKey("body")) {
            Object bodyObj = step.rawParams().get("body");
            if (bodyObj instanceof Map) {
                errors.add("Step '" + step.name() + "': elasticsearch 'body' parameter was parsed as a Map (likely due to multi-line JSON without the '|' character). Please use the 'queryDsl' parameter instead for multi-line JSON objects, or format 'body' as a multi-line string using 'body: |'.");
            }
        }
    }

    private void checkReferences(final RecipeDefinition def,
                                 final List<String> errors) {
        Set<String> validSteps = new HashSet<>();
        Pattern p = Pattern.compile("\\$\\{([a-zA-Z0-9._-]+)\\}");
        for (RecipeStep step : def.steps()) {
            Matcher m = p.matcher(step.toString());
            while (m.find()) {
                String ref = m.group(1);
                if (ref.contains(".") && !ref.startsWith("system.")) {
                    String sRef = ref.split("\\.")[0];
                    if (!validSteps.contains(sRef)) {
                        errors.add("Unknown ref: " + sRef);
                    }
                }
            }
            validSteps.add(step.name());
        }
    }
}
