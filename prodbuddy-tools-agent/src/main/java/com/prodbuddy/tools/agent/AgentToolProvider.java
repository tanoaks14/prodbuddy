package com.prodbuddy.tools.agent;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolProvider;

import java.util.Collection;
import java.util.List;

public final class AgentToolProvider implements ToolProvider {
    @Override
    public Collection<Tool> getTools() {
        return List.of(new AgentTool());
    }
}
