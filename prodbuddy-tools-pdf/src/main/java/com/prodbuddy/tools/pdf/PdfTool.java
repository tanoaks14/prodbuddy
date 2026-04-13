package com.prodbuddy.tools.pdf;

import java.nio.file.Path;
import java.util.Set;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolContext;
import com.prodbuddy.core.tool.ToolExecutionException;
import com.prodbuddy.core.tool.ToolMetadata;
import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolResponse;

public final class PdfTool implements Tool {

    private static final String NAME = "pdf";
    private final OpenDataLoaderPdfAdapter adapter;

    public PdfTool(OpenDataLoaderPdfAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(NAME, "PDF read/create tool", Set.of("pdf.read", "pdf.create"));
    }

    @Override
    public boolean supports(ToolRequest request) {
        return "pdf".equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(ToolRequest request, ToolContext context) throws ToolExecutionException {
        String pathText = String.valueOf(request.payload().getOrDefault("path", ""));
        if (pathText.isBlank()) {
            return ToolResponse.failure("PDF_BAD_REQUEST", "path is required");
        }

        Path path = Path.of(pathText);
        if ("read".equalsIgnoreCase(request.operation())) {
            return ToolResponse.ok(adapter.extract(path));
        }

        if ("create".equalsIgnoreCase(request.operation())) {
            String content = String.valueOf(request.payload().getOrDefault("content", ""));
            return ToolResponse.ok(adapter.create(path, content));
        }

        return ToolResponse.failure("PDF_UNSUPPORTED_OPERATION", "supported operations are read, create");
    }
}
