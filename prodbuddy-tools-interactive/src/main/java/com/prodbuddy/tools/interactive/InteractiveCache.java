package com.prodbuddy.tools.interactive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;

/**
 * Handles local persistence of interactive tool selections per developer.
 */
public final class InteractiveCache {

    /** Path to cache file. */
    private static final String CACHE_FILE_PATH =
            System.getProperty("user.home") + File.separator + ".prodbuddy"
                    + File.separator + "interactive-cache.json";

    /** ObjectMapper. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InteractiveCache() {
    }

    /**
     * Gets a cached value by key.
     * @param key The cache key
     * @return The cached value, or null if not found
     */
    public static String get(final String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            File file = new File(CACHE_FILE_PATH);
            if (!file.exists()) {
                return null;
            }
            JsonNode root = MAPPER.readTree(file);
            JsonNode valueNode = root.get(key);
            if (valueNode != null && !valueNode.isNull()) {
                return valueNode.isTextual() ? valueNode.asText()
                        : valueNode.toString();
            }
        } catch (IOException e) {
            // Ignore read errors, just return null
        }
        return null;
    }

    /**
     * Puts a value in the cache.
     * @param key The cache key
     * @param value The value to cache (can be JSON string or plain text)
     */
    public static void put(final String key, final String value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        try {
            File file = new File(CACHE_FILE_PATH);
            file.getParentFile().mkdirs();
            ObjectNode root = getOrCreateRoot(file);

            try {
                JsonNode valueNode = MAPPER.readTree(value);
                root.set(key, valueNode);
            } catch (Exception e) {
                root.put(key, value);
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, root);
        } catch (IOException e) {
            // Ignore write errors
        }
    }

    /**
     * Gets or creates the root node.
     * @param file The file
     * @return The root node
     */
    private static ObjectNode getOrCreateRoot(final File file) {
        if (!file.exists()) {
            return MAPPER.createObjectNode();
        }
        try {
            JsonNode existing = MAPPER.readTree(file);
            return existing.isObject() ? (ObjectNode) existing
                    : MAPPER.createObjectNode();
        } catch (IOException e) {
            return MAPPER.createObjectNode();
        }
    }
}
