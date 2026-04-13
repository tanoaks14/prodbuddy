package com.prodbuddy.core.tool;

import java.util.Objects;

public record ToolError(String code, String message) {

    public ToolError  {
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(message, "message is required");
    }
}
