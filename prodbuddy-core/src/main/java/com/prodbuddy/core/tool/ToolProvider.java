package com.prodbuddy.core.tool;

import java.util.Collection;

/**
 * SPI interface for providing tools to the registry.
 * Implementations should be registered in META-INF/services/com.prodbuddy.core.tool.ToolProvider.
 */
public interface ToolProvider {
    /**
     * Returns a collection of tools to be registered.
     */
    Collection<Tool> getTools();
}
