package com.prodbuddy.core.system;

import com.prodbuddy.core.tool.ToolMetadata;

import java.util.Set;

public record ToolInfo(String name, String description, Set<String> capabilities) {

    public static ToolInfo from(ToolMetadata metadata) {
        return new ToolInfo(metadata.name(), metadata.description(), metadata.capabilities());
    }
}
