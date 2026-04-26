package com.prodbuddy.core.tool;

import java.util.Collections;
import java.util.Map;

/**
 * Defines the visual styling for a tool in observations (e.g. Mermaid).
 */
public final class ToolStyling {

    /** Default styling. */
    public static final ToolStyling DEFAULT = new ToolStyling("", "black", "white");

    private final String actorColor;
    private final String textColor;
    private final String noteColor;
    private final Map<String, String> extras;

    /**
     * Constructor.
     * @param actorColor hex color for the participant
     * @param textColor hex color for the text
     * @param noteColor hex color for the notes
     */
    public ToolStyling(final String actorColor,
                       final String textColor,
                       final String noteColor) {
        this(actorColor, textColor, noteColor, Map.of());
    }

    /**
     * Full constructor.
     * @param actorColor actor color
     * @param textColor text color
     * @param noteColor note color
     * @param extras extra hints
     */
    public ToolStyling(final String actorColor,
                       final String textColor,
                       final String noteColor,
                       final Map<String, String> extras) {
        this.actorColor = actorColor;
        this.textColor = textColor;
        this.noteColor = noteColor;
        this.extras = extras == null ? Map.of()
                : Collections.unmodifiableMap(extras);
    }

    /** @return actor color. */
    public String actorColor() {
        return actorColor;
    }

    /** @return text color. */
    public String textColor() {
        return textColor;
    }

    /** @return note color. */
    public String noteColor() {
        return noteColor;
    }

    /** @return extra styling hints. */
    public Map<String, String> extras() {
        return extras;
    }

    /**
     * Converts styling to a metadata map.
     * @return map of styling hints
     */
    public Map<String, String> toMetadata() {
        Map<String, String> meta = new java.util.HashMap<>(extras);
        if (actorColor != null && !actorColor.isEmpty()) {
            meta.put("actorColor", actorColor);
        }
        if (textColor != null && !textColor.isEmpty()) {
            meta.put("textColor", textColor);
        }
        if (noteColor != null && !noteColor.isEmpty()) {
            meta.put("noteColor", noteColor);
        }
        return meta;
    }
}
