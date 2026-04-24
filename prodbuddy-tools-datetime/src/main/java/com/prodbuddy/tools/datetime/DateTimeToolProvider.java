package com.prodbuddy.tools.datetime;

import java.util.List;
import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolProvider;

public final class DateTimeToolProvider implements ToolProvider {
    @Override
    public List<Tool> getTools() {
        return List.of(new TimeConverterTool());
    }
}
