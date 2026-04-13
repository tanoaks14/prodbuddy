package com.prodbuddy.tools.pdf;

import java.nio.file.Path;
import java.util.Map;

public interface OpenDataLoaderPdfAdapter {

    Map<String, Object> extract(Path filePath);

    Map<String, Object> create(Path filePath, String content);
}
