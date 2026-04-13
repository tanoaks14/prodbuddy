package com.prodbuddy.tools.pdf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class LocalOpenDataLoaderPdfAdapter implements OpenDataLoaderPdfAdapter {

    @Override
    public Map<String, Object> extract(Path filePath) {
        boolean exists = Files.exists(filePath);
        return Map.of(
                "provider", "opendataloader-pdf-adapter",
                "mode", "local",
                "file", filePath.toString(),
                "exists", exists,
                "note", "Adapter boundary in place. Wire concrete OpenDataLoader runtime in this class."
        );
    }

    @Override
    public Map<String, Object> create(Path filePath, String content) {
        return Map.of(
                "provider", "opendataloader-pdf-adapter",
                "mode", "local",
                "file", filePath.toString(),
                "contentLength", content == null ? 0 : content.length(),
                "note", "Creation contract is implemented at adapter level for future OpenDataLoader wiring."
        );
    }
}
