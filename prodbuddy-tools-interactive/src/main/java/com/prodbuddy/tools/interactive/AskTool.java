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
                Set.of("interactive.ask")
        );
    }

    @Override
    public boolean supports(final ToolRequest request) {
        return "ask".equals(request.operation());
    }

    @Override
    public ToolResponse execute(final ToolRequest request,
                                final ToolContext context) {
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
}
