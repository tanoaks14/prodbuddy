package com.prodbuddy.tools.git;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolProvider;

import java.util.Collection;
import java.util.List;

public final class GitToolProvider implements ToolProvider {
    @Override
    public Collection<Tool> getTools() {
        return List.of(new GitTool());
    }
}
