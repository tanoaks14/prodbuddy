package com.prodbuddy.core.tool;

public interface Tool {

    ToolMetadata metadata();

    boolean supports(ToolRequest request);

    ToolResponse execute(ToolRequest request, ToolContext context) throws ToolExecutionException;
}
