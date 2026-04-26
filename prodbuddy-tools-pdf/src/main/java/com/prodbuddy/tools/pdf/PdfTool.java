package com.prodbuddy.tools.pdf;

import com.prodbuddy.core.tool.*;

import java.nio.file.Path;
import java.util.Set;

public final class PdfTool implements Tool {

    private static final String NAME = "pdf";
    private final OpenDataLoaderPdfAdapter adapter;
    private final com.prodbuddy.observation.SequenceLogger seqLog;

    public PdfTool(OpenDataLoaderPdfAdapter adapter) {
        this.adapter = adapter;
        this.seqLog = com.prodbuddy.observation.ObservationContext.getLogger();
    }

    @Override
    public com.prodbuddy.core.tool.ToolStyling styling() {
        return new com.prodbuddy.core.tool.ToolStyling("#FFEBEE", "#B71C1C", "#FFCDD2", "📑 PDF", java.util.Map.of());
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
        seqLog.logSequence("AgentLoopOrchestrator", NAME, "execute", "Processing PDF operation", 
                styling().toMetadata());
        String pathText = String.valueOf(request.payload().getOrDefault("path", ""));
        if (pathText.isBlank()) {
            return ToolResponse.failure("PDF_BAD_REQUEST", "path is required");
        }

        Path path = Path.of(pathText);
        if ("read".equalsIgnoreCase(request.operation())) {
            seqLog.logSequence(NAME, "OpenDataLoaderPdfAdapter", "extract", "Delegating read to adapter",
                    styling().toMetadata());
            return ToolResponse.ok(adapter.extract(path));
        }

        if ("create".equalsIgnoreCase(request.operation())) {
            String content = String.valueOf(request.payload().getOrDefault("content", ""));
            seqLog.logSequence(NAME, "OpenDataLoaderPdfAdapter", "create", "Delegating create to adapter",
                    styling().toMetadata());
            return ToolResponse.ok(adapter.create(path, content));
        }

        return ToolResponse.failure("PDF_UNSUPPORTED_OPERATION", "supported operations are read, create");
    }
}
