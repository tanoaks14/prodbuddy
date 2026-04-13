package com.prodbuddy.core.tool;

import java.util.Optional;

public interface ToolRouter {

    Optional<String> route(ToolRequest request);
}
