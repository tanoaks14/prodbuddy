package com.prodbuddy.tools.interactive;

import java.util.List;
import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolProvider;

public final class InteractiveToolProvider implements ToolProvider {
    @Override
    public List<Tool> getTools() {
        return List.of(new AskTool());
    }
}
