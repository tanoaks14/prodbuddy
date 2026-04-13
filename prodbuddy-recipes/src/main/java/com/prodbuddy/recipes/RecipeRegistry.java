package com.prodbuddy.recipes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class RecipeRegistry {

    private final Map<String, RecipeDefinition> byName;
    private final Map<String, List<RecipeDefinition>> byTag;

    private RecipeRegistry(Map<String, RecipeDefinition> byName, Map<String, List<RecipeDefinition>> byTag) {
        this.byName = Collections.unmodifiableMap(byName);
        this.byTag = Collections.unmodifiableMap(byTag);
    }

    public static RecipeRegistry loadFrom(Path directory) {
        Map<String, RecipeDefinition> byName = new LinkedHashMap<>();
        Map<String, List<RecipeDefinition>> byTag = new LinkedHashMap<>();
        if (!Files.isDirectory(directory)) {
            return new RecipeRegistry(byName, byTag);
        }
        RecipeLoader loader = new RecipeLoader();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(f -> f.toString().endsWith(".md"))
                 .sorted()
                 .forEach(f -> loadOne(f, loader, byName, byTag));
        } catch (IOException ignored) {
            // return whatever was indexed so far
        }
        return new RecipeRegistry(byName, byTag);
    }

    private static void loadOne(
            Path file,
            RecipeLoader loader,
            Map<String, RecipeDefinition> byName,
            Map<String, List<RecipeDefinition>> byTag
    ) {
        try {
            RecipeDefinition def = loader.load(file);
            byName.put(def.name(), def);
            for (String tag : def.tags()) {
                byTag.computeIfAbsent(tag, t -> new ArrayList<>()).add(def);
            }
        } catch (IOException ignored) {
            // skip unreadable files silently
        }
    }

    public RecipeDefinition findByName(String name) {
        return byName.get(name);
    }

    public List<RecipeDefinition> findByTag(String tag) {
        return byTag.getOrDefault(tag, List.of());
    }

    public List<RecipeDefinition> all() {
        return new ArrayList<>(byName.values());
    }
}
