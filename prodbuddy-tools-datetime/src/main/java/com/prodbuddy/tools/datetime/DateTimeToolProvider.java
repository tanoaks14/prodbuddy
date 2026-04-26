package com.prodbuddy.tools.datetime;

import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolProvider;

import java.util.List;

public final class DateTimeToolProvider implements ToolProvider {
    @Override
    public List<Tool> getTools() {
        return List.of(new TimeConverterTool());
    }
}
