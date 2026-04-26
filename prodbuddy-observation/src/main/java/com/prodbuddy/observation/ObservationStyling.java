package com.prodbuddy.observation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for actor styling used by diagram generators.
 * This allows tools to register their own styles without the observation
 * module depending on the core tool interfaces.
 */
public final class ObservationStyling {

    /** The registry map. */
    private static final Map<String, Styling> REGISTRY =
            new ConcurrentHashMap<>();

    private ObservationStyling() {
    }

    /**
     * Registers styling for an actor.
     * @param actorId The actor ID (e.g. "newrelic")
     * @param displayName The display name (e.g. "☁️ New Relic")
     * @param color The hex color (e.g. "#B2DFDB")
     */
    public static void register(final String actorId,
                                final String displayName,
                                final String color) {
        if (actorId != null) {
            REGISTRY.put(actorId.toLowerCase(),
                    new Styling(displayName, color));
        }
    }

    /**
     * Gets styling for an actor.
     * @param actorId The actor ID
     * @return The styling or null if not registered
     */
    public static Styling get(final String actorId) {
        if (actorId == null) {
            return null;
        }
        return REGISTRY.get(actorId.toLowerCase());
    }

    /**
     * Styling data for an actor.
     */
    public static final class Styling {
        /** The display name. */
        private final String displayName;
        /** The hex color. */
        private final String color;

        private Styling(final String name, final String col) {
            this.displayName = name;
            this.color = col;
        }

        /**
         * Gets the display name.
         * @return The display name
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Gets the color.
         * @return The hex color
         */
        public String getColor() {
            return color;
        }
    }
}
