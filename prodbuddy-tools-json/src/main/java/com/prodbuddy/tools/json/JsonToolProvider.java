package com.prodbuddy.tools.json;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolProvider;

import java.util.Collection;
import java.util.List;

public final class JsonToolProvider implements ToolProvider {
    @Override
    public Collection<Tool> getTools() {
        return List.of(new JsonTool(new JsonAnalyzer()));
    }
}
