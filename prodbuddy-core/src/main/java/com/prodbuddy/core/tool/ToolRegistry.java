package com.prodbuddy.core.tool;

import java.util.*;

public final class ToolRegistry {

    private final Map<String, Tool> toolsByName;
    private final com.prodbuddy.observation.SequenceLogger seqLog;

    public ToolRegistry(Collection<Tool> tools) {
        this.toolsByName = new LinkedHashMap<>();
        for (Tool tool : tools) {
            String name = tool.metadata().name();
            toolsByName.put(name, tool);
            ToolStyling style = tool.styling();
            if (style != null) {
                com.prodbuddy.observation.ObservationStyling.register(
                        name, style.displayName(), style.actorColor());
            }
        }
        registerDefaults();
        this.seqLog = com.prodbuddy.observation.ObservationContext.getLogger();
    }

    private void registerDefaults() {
        com.prodbuddy.observation.ObservationStyling.register("client", "👤 Client", "#E1F5FE");
        com.prodbuddy.observation.ObservationStyling.register("orchestrator", "🎼 Orchestrator", "#F5F5F5");
        com.prodbuddy.observation.ObservationStyling.register("reciperunner", "📝 RecipeRunner", "#F5F5F5");
        com.prodbuddy.observation.ObservationStyling.register("recipeclihandler", "⌨️ RecipeCLI", "#F5F5F5");
        com.prodbuddy.observation.ObservationStyling.register("ruleengine", "🧠 RuleEngine", "#F5F5F5");
        com.prodbuddy.observation.ObservationStyling.register("toolrouter", "🚦 ToolRouter", "#F5F5F5");
        com.prodbuddy.observation.ObservationStyling.register("user", "👤 User", "#FFF9C4");
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
