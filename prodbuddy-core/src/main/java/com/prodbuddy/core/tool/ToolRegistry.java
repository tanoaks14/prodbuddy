package com.prodbuddy.core.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

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

    /**
     * Discovers tools via SPI (ToolProvider).
     */
    public static ToolRegistry discover() {
        List<Tool> discovered = new ArrayList<>();
        ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class);
        for (ToolProvider provider : loader) {
            discovered.addAll(provider.getTools());
        }
        return new ToolRegistry(discovered);
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
