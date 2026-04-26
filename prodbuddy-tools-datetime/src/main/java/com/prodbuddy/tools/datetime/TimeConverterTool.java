package com.prodbuddy.tools.datetime;

import com.prodbuddy.core.tool.*;
import com.prodbuddy.observation.ObservationContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

public final class TimeConverterTool implements Tool {

    @Override
    public ToolMetadata metadata() {
        return new ToolMetadata(
                "datetime",
                "Tool for date and time transformations.",
                Set.of("datetime.convert")
        );
    }

    @Override
    public boolean supports(final ToolRequest request) {
        String op = request.operation();
        return "convert".equals(op) || "now".equals(op);
    }

    @Override
    public com.prodbuddy.core.tool.ToolStyling styling() {
        return new com.prodbuddy.core.tool.ToolStyling("#E8F5E9", "#1B5E20", "#C8E6C9", "🕒 DateTime", java.util.Map.of());
    }

    @Override
    public ToolResponse execute(final ToolRequest request,
                                final ToolContext context) {
        String op = request.operation();
        ObservationContext.log("Orchestrator", "DateTime", op, "requested", 
                styling().toMetadata("DateTime"));
        if ("now".equals(op)) {
            return ToolResponse.ok(Map.of("value", Instant.now().toString(),
                    "status", "now"));
        }
        
        String input = String.valueOf(request.payload().get("value"));
        String from = String.valueOf(request.payload()
                .getOrDefault("from", "iso"));
        String to = String.valueOf(request.payload()
                .getOrDefault("to", "epoch"));
        String zone = String.valueOf(request.payload()
                .getOrDefault("zone", "UTC"));

        try {
            String result = convert(input, from, to, zone);
            return ToolResponse.ok(Map.of("value", result,
                    "status", "converted"));
        } catch (Exception e) {
            return ToolResponse.failure("CONVERSION_ERROR", e.getMessage());
        }
    }

    private String convert(final String input, final String from,
                           final String to, final String zone)
            throws Exception {
        Instant instant;
        if ("iso".equalsIgnoreCase(from)) {
            instant = Instant.parse(input);
        } else if ("epoch".equalsIgnoreCase(from)) {
            instant = Instant.ofEpochMilli(Long.parseLong(input));
        } else {
            throw new IllegalArgumentException("Unsupported: " + from);
        }

        if ("epoch".equalsIgnoreCase(to)) {
            return String.valueOf(instant.toEpochMilli());
        } else if ("iso".equalsIgnoreCase(to)) {
            return instant.toString();
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(to)
                .withZone(ZoneId.of(zone));
        return fmt.format(instant);
    }
}
