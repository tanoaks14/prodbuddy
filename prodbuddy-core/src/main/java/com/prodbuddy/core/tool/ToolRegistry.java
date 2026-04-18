package com.prodbuddy.core.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {

    private final Map<String, Tool> toolsByName;
    private final com.prodbuddy.observation.SequenceLogger seqLog;

    public ToolRegistry(Collection<Tool> tools) {
        this.toolsByName = new LinkedHashMap<>();
        for (Tool tool : tools) {
            toolsByName.put(tool.metadata().name(), tool);
        }
        this.seqLog = new com.prodbuddy.observation.Slf4jSequenceLogger(ToolRegistry.class);
    }

    public Optional<Tool> find(String toolName) {
        seqLog.logSequence("ToolRegistry", "InternalMap", "get", "Look up exact match");
        return Optional.ofNullable(toolsByName.get(toolName));
    }

    public Collection<Tool> all() {
        return toolsByName.values();
    }

    public List<ToolMetadata> metadata() {
        return toolsByName.values().stream().map(Tool::metadata).toList();
    }
}
