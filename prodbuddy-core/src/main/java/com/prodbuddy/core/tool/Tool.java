package com.prodbuddy.core.tool;

public interface Tool {

    ToolMetadata metadata();

    boolean supports(ToolRequest request);

    ToolResponse execute(ToolRequest request, ToolContext context) throws ToolExecutionException;

    /**
     * @return the visual styling for this tool.
     */
    default ToolStyling styling() {
        return ToolStyling.DEFAULT;
    }
}
