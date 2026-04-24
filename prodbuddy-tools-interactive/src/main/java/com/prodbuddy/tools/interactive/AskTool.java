package com.prodbuddy.tools.interactive;

import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

public final class AskTool implements Tool {

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
            return handleSelect(request);
        }
        return handleAsk(request);
    }

    private ToolResponse handleAsk(final ToolRequest request) {
        String prompt = String.valueOf(request.payload().getOrDefault(
                "prompt", "Please provide clarification:"));
        System.out.println("\n[AGENT REQUEST] " + prompt);

        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextLine()) {
            String response = scanner.nextLine();
            return ToolResponse.ok(Map.of("answer", response,
                    "status", "answered"));
        }
        return ToolResponse.failure("NO_INPUT", "No input received.");
    }

    private ToolResponse handleSelect(final ToolRequest request) {
        String data = String.valueOf(request.payload().getOrDefault("data", "[]"));
        String prompt = String.valueOf(request.payload().getOrDefault("prompt",
                "Please select an option:"));
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(data);
            if (!node.isArray()) {
                return ToolResponse.failure("SELECT_BAD_DATA", "Data must be a JSON array");
            }
            System.out.println("\n[AGENT SELECTION] " + prompt);
            for (int i = 0; i < node.size(); i++) {
                System.out.println((i + 1) + ". " + node.get(i).toString());
            }
            System.out.print("Enter choice (1-" + node.size() + "): ");
            Scanner scanner = new Scanner(System.in);
            if (scanner.hasNextInt()) {
                int choice = scanner.nextInt();
                if (choice >= 1 && choice <= node.size()) {
                    com.fasterxml.jackson.databind.JsonNode selected = node.get(choice - 1);
                    return ToolResponse.ok(mapper.convertValue(selected, Map.class));
                }
            }
            return ToolResponse.failure("INVALID_CHOICE", "Selection out of range or invalid.");
        } catch (Exception e) {
            return ToolResponse.failure("SELECT_ERROR", e.getMessage());
        }
    }
}
