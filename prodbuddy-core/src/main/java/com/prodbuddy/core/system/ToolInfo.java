package com.prodbuddy.core.system;

import java.util.Set;

import com.prodbuddy.core.tool.ToolMetadata;

public record ToolInfo(String name, String description, Set<String> capabilities) {

    public static ToolInfo from(ToolMetadata metadata) {
        return new ToolInfo(metadata.name(), metadata.description(), metadata.capabilities());
    }
}
