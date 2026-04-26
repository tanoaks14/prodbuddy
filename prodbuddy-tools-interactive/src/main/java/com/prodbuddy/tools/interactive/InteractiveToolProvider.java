package com.prodbuddy.tools.interactive;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolProvider;

import java.util.List;

public final class InteractiveToolProvider implements ToolProvider {
    @Override
    public List<Tool> getTools() {
        return List.of(new AskTool());
    }
}
