package com.prodbuddy.tools.graphql;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolProvider;
import java.util.Collection;
import java.util.List;

public final class GraphQLToolProvider implements ToolProvider {
    @Override
    public Collection<Tool> getTools() {
        return List.of(new GraphQLTool());
    }
}
