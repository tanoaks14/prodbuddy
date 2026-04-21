package com.prodbuddy.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.prodbuddy.recipes.RecipeStep;

/**
 * Interactive wizard that guides the user through creating a recipe .md file.
 * Prompts for name/description/tags, then collects steps one at a time,
 * and writes the result under the configured RECIPES_DIR.
 */
public final class RecipeAddWizard {

    private final Map<String, String> environment;

    public RecipeAddWizard(Map<String, String> environment) {
        this.environment = environment;
    }

    public void run() {
        String dir = environment.getOrDefault("RECIPES_DIR", "recipes");
        System.out.println("Creating new recipe in: " + dir + "/");
        Map<String, String> frontmatter = promptFrontmatter();
        List<RecipeStep> steps = promptSteps();
        try {
            write(dir, frontmatter, steps);
        } catch (IOException ex) {
            System.out.println("Failed to save recipe: " + ex.getMessage());
        }
    }

    private Map<String, String> promptFrontmatter() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("name", ConsoleInput.readLine("Recipe name (e.g. payment-timeout-debug): "));
        result.put("description", ConsoleInput.readLine("Description: "));
        String tags = ConsoleInput.readLine("Tags (comma-separated, e.g. payment,5xx): ");
        result.put("tags", "[" + tags + "]");
        return result;
    }

    private List<RecipeStep> promptSteps() {
        List<RecipeStep> steps = new ArrayList<>();
        int i = 1;
        while (true) {
            String more = ConsoleInput.readLine("Add step " + i + "? (y/n): ");
            if (more == null || !"y".equalsIgnoreCase(more.trim())) {
                break;
            }
            steps.add(promptStep());
            i++;
        }
        return steps;
    }

    private RecipeStep promptStep() {
        String name = ConsoleInput.readLine("  Step name: ");
        String tool = ConsoleInput.readLine("  Tool (http/splunk/elasticsearch/kubectl/newrelic): ");
        String operation = ConsoleInput.readLine("  Operation: ");
        String condition = ConsoleInput.readLine("  Condition (optional): ");
        Map<String, Object> params = promptParams();
        return new RecipeStep(name, tool, operation, condition, params);
    }

    private Map<String, Object> promptParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        System.out.println("  Enter params (key: value). Leave key blank to finish.");
        while (true) {
            String key = ConsoleInput.readLine("    key: ");
            if (key == null || key.isBlank()) {
                break;
            }
            String value = ConsoleInput.readLine("    " + key.trim() + ": ");
            params.put(key.trim(), value != null ? value : "");
        }
        return params;
    }

    private void write(String dir, Map<String, String> frontmatter, List<RecipeStep> steps) throws IOException {
        String name = frontmatter.get("name");
        if (name == null || name.isBlank()) {
            System.out.println("Recipe name is required. Aborting.");
            return;
        }
        Path directory = Path.of(dir);
        Files.createDirectories(directory);
        Path file = directory.resolve(sanitizeName(name) + ".md");
        Files.writeString(file, buildMarkdown(frontmatter, steps));
        System.out.println("Recipe saved: " + file);
    }

    private String sanitizeName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9\\-]", "-");
    }

    private String buildMarkdown(Map<String, String> frontmatter, List<RecipeStep> steps) {
        StringBuilder sb = new StringBuilder("---\n");
        for (Map.Entry<String, String> entry : frontmatter.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("---\n\n");
        for (RecipeStep step : steps) {
            appendStepBlock(sb, step);
        }
        return sb.toString();
    }

    private void appendStepBlock(StringBuilder sb, RecipeStep step) {
        sb.append("## ").append(step.name()).append("\n");
        sb.append("tool: ").append(step.tool()).append("\n");
        sb.append("operation: ").append(step.operation()).append("\n");
        if (step.condition() != null && !step.condition().isBlank()) {
            sb.append("condition: ").append(step.condition()).append("\n");
        }
        for (Map.Entry<String, Object> entry : step.rawParams().entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("\n");
    }
}
