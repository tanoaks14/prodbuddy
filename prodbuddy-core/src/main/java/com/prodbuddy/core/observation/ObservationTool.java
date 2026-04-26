package com.prodbuddy.core.observation;

import com.prodbuddy.core.tool.*;
import com.prodbuddy.observation.LocalDiagramRenderer;
import com.prodbuddy.observation.RecordingSequenceLogger;

import java.util.Map;
import java.util.Set;

/**
 * Tool for interacting with the observation system.
 * Located in prodbuddy-core to avoid circular dependencies with observation module.
 */
public final class ObservationTool implements Tool {

    private final RecordingSequenceLogger logger;

    public ObservationTool(RecordingSequenceLogger logger) {
        this.logger = logger;
    }

    @Override
    public com.prodbuddy.core.tool.ToolStyling styling() {
        return new com.prodbuddy.core.tool.ToolStyling("#F5F5F5", "#616161", "#EEEEEE", "📊 Observation", java.util.Map.of());
    }

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                "observation",
                "System observation and tracing tool",
                Set.of("mermaid", "clear")
        );
    }

    @Override
    public boolean supports(ToolRequest request) {
        return "observation".equalsIgnoreCase(request.intent());
    }

    @Override
    public ToolResponse execute(final ToolRequest request, final ToolContext context) {
        String op = request.operation().toLowerCase();
        return switch (op) {
            case "mermaid" -> ToolResponse.ok(Map.of("mermaid", logger.toMermaid(), "body", logger.toMermaid()));
            case "render" -> renderTrace(request, context);
            case "clear" -> {
                logger.clear();
                yield ToolResponse.ok(Map.of("status", "cleared"));
            }
            default -> ToolResponse.failure("OBS_OPERATION", "unsupported: " + op);
        };
    }

    private ToolResponse renderTrace(final ToolRequest request, final ToolContext context) {
        String format = String.valueOf(request.payload().getOrDefault("format", "png")).toLowerCase();
        
        try {
            LocalDiagramRenderer local = new LocalDiagramRenderer();
            byte[] image = local.renderSequence(logger.getEvents());
            
            java.io.File dir = new java.io.File("traces");
            if (!dir.exists() && !dir.mkdirs()) {
                return ToolResponse.failure("FILE_ERROR", "Could not create traces directory");
            }
            
            String fileName = "trace_" + System.currentTimeMillis() + "." + format;
            java.io.File file = new java.io.File(dir, fileName);
            java.nio.file.Files.write(file.toPath(), image);
            
            return ToolResponse.ok(Map.of(
                "path", file.getAbsolutePath(),
                "format", format,
                "url", file.toURI().toString()
            ));
        } catch (Exception e) {
            return ToolResponse.failure("RENDER_FAILED", e.getMessage());
        }
    }
}
