package com.prodbuddy.core.tool;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record ToolResponse(boolean success, Map<String, Object> data, List<ToolError> errors) {

    public ToolResponse   {
        data = data == null ? Map.of() : Collections.unmodifiableMap(data);
        errors = errors == null ? List.of() : Collections.unmodifiableList(errors);
    }

    public static ToolResponse ok(Map<String, Object> data) {
        return new ToolResponse(true, data, List.of());
    }

    public static ToolResponse failure(String code, String message) {
        return new ToolResponse(false, Map.of(), List.of(new ToolError(code, message)));
    }
}
