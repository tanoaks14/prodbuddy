package com.prodbuddy.tools.interactive;

import com.prodbuddy.core.tool.*;
import com.prodbuddy.observation.ObservationContext;

import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public final class AskTool implements Tool {
    
    @Override
    public com.prodbuddy.core.tool.ToolStyling styling() {
        return new com.prodbuddy.core.tool.ToolStyling("#E3F2FD", "#0D47A1", "#BBDEFB", "💬 Interactive", java.util.Map.of());
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                "interactive",
                "Tool for interactive user communication.",
                Set.of("interactive.ask", "interactive.select")
        );
    }

    @Override
    public boolean supports(final ToolRequest request) {
        return "ask".equals(request.operation()) || "select".equals(request.operation());
    }

    @Override
    public ToolResponse execute(final ToolRequest request,
                                final ToolContext context) {
        if ("select".equalsIgnoreCase(request.operation())) {
            return handleSelect(request, context);
        }
        return handleAsk(request, context);
    }

    private ToolResponse handleAsk(final ToolRequest request,
                                   final ToolContext context) {
        String prompt = String.valueOf(request.payload().getOrDefault("prompt",
                "Please provide clarification:"));
        String cacheKey = (String) request.payload().get("cacheKey");
        boolean forceInteractive = Boolean.parseBoolean(
                context.envOrDefault("PRODBUDDY_FORCE_INTERACTIVE",
                        "false"));
        boolean autoSelect = !forceInteractive && Boolean.parseBoolean(
                String.valueOf(request.payload().getOrDefault(
                        "autoSelect", "false")));

        String cachedValue = cacheKey != null ? InteractiveCache.get(cacheKey)
                : null;
        if (cachedValue != null && autoSelect) {
            System.out.println("\n[AGENT REQUEST] " + prompt
                    + " (Auto-selected: " + cachedValue + ")");
            ObservationContext.log("User", "Interactive", "answer",
                    cachedValue, styling().toMetadata("Interactive"));
            return ToolResponse.ok(Map.of("answer", cachedValue, "status",
                    "answered-from-cache"));
        }

        System.out.print("\n[AGENT REQUEST] " + prompt
                + (cachedValue != null ? " [Default: " + cachedValue + "]" : "")
                + "\n> ");
        return processAskInput(cacheKey, cachedValue);
    }

    private ToolResponse processAskInput(final String cacheKey,
                                         final String cachedValue) {
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextLine()) {
            String response = scanner.nextLine().trim();
            if (response.isEmpty() && cachedValue != null) {
                response = cachedValue;
            }
            if (cacheKey != null && !response.isEmpty()) {
                InteractiveCache.put(cacheKey, response);
            }
            ObservationContext.log("User", "Interactive", "answer", response,
                    styling().toMetadata("Interactive"));
            return ToolResponse.ok(Map.of("answer", response,
                    "status", "answered"));
        }
        return ToolResponse.failure("NO_INPUT", "No input received.");
    }

    private ToolResponse handleSelect(final ToolRequest request,
                                      final ToolContext context) {
        try {
            String data = String.valueOf(request.payload().getOrDefault("data",
                    "[]"));
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node =
                    mapper.readTree(data);
            if (!node.isArray()) {
                return ToolResponse.failure("SELECT_BAD_DATA",
                        "Data must be a JSON array");
            }
            return processSelect(request, context, mapper, node);
        } catch (Exception e) {
            return ToolResponse.failure("SELECT_ERROR", e.getMessage());
        }
    }

    private ToolResponse processSelect(final ToolRequest req,
                                       final ToolContext context,
                                       final com.fasterxml.jackson.databind.ObjectMapper mapper,
                                       final com.fasterxml.jackson.databind.JsonNode node) {
        String prompt = String.valueOf(req.payload().getOrDefault("prompt",
                "Please select an option:"));
        String cacheKey = (String) req.payload().get("cacheKey");
        boolean forceInteractive = Boolean.parseBoolean(
                context.envOrDefault("PRODBUDDY_FORCE_INTERACTIVE",
                        "false"));
        boolean autoSelect = !forceInteractive && Boolean.parseBoolean(
                String.valueOf(req.payload().getOrDefault(
                        "autoSelect", "false")));

        int defaultIndex = findCachedIndex(cacheKey, node, mapper);
        if (defaultIndex != -1 && autoSelect) {
            com.fasterxml.jackson.databind.JsonNode sld = node.get(defaultIndex);
            System.out.println("\n[AGENT SELECTION] " + prompt
                    + " (Auto-selected Option " + (defaultIndex + 1) + ")");
            ObservationContext.log("User", "Interactive", "select",
                    sld.toString(), styling().toMetadata("Interactive"));
            return ToolResponse.ok(mapper.convertValue(sld, Map.class));
        }

        printSelectOptions(prompt, node, defaultIndex);
        return readSelection(node, cacheKey, defaultIndex, mapper);
    }

    private void printSelectOptions(final String prompt,
                                    final com.fasterxml.jackson.databind.JsonNode node,
                                    final int defaultIndex) {
        System.out.println("\n[AGENT SELECTION] " + prompt);
        for (int i = 0; i < node.size(); i++) {
            com.fasterxml.jackson.databind.JsonNode item = node.get(i);
            String display = item.toString();
            if (item.isObject()) {
                if (item.has("name")) {
                    display = item.get("name").asText();
                    if (item.has("guid")) {
                        display += " (" + item.get("guid").asText() + ")";
                    }
                } else if (item.has("id")) {
                    display = "ID: " + item.get("id").asText();
                }
            } else if (item.isTextual()) {
                display = item.asText();
            }
            System.out.println((i + 1) + ". " + display);
        }
        String defText = defaultIndex != -1 ? " [Default: "
                + (defaultIndex + 1) + "]" : "";
        System.out.print("Enter choice (1-" + node.size() + ")"
                + defText + ": ");
    }

    private int findCachedIndex(final String cacheKey,
                                final com.fasterxml.jackson.databind.JsonNode node,
                                final com.fasterxml.jackson.databind.ObjectMapper mapper) {
        if (cacheKey == null) {
            return -1;
        }
        String cachedStr = InteractiveCache.get(cacheKey);
        if (cachedStr == null) {
            return -1;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode cachedNode =
                    mapper.readTree(cachedStr);
            for (int i = 0; i < node.size(); i++) {
                if (node.get(i).equals(cachedNode)) {
                    return i;
                }
            }
        } catch (Exception e) {
            for (int i = 0; i < node.size(); i++) {
                if (node.get(i).toString().equals(cachedStr)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private ToolResponse readSelection(
            final com.fasterxml.jackson.databind.JsonNode node,
            final String cacheKey, final int defIdx,
            final com.fasterxml.jackson.databind.ObjectMapper mapper) {
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextLine()) {
            String input = scanner.nextLine().trim();
            int choice = -1;
            if (input.isEmpty() && defIdx != -1) {
                choice = defIdx + 1;
            } else if (!input.isEmpty()) {
                try {
                    choice = Integer.parseInt(input);
                } catch (NumberFormatException ignored) { }
            }
            if (choice >= 1 && choice <= node.size()) {
                com.fasterxml.jackson.databind.JsonNode sld =
                        node.get(choice - 1);
                if (cacheKey != null) {
                    InteractiveCache.put(cacheKey, sld.toString());
                }
                ObservationContext.log("User", "Interactive", "select",
                        sld.toString(), styling().toMetadata("Interactive"));
                return ToolResponse.ok(mapper.convertValue(sld, Map.class));
            }
        }
        return ToolResponse.failure("INVALID_CHOICE",
                "Selection out of range or invalid.");
    }
}
