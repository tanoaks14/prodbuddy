package com.prodbuddy.tools.codecontext;

import java.util.Map;

/** Per-class complexity metrics computed during AST extraction. */
public record ClassMetrics(
        String classFqn,
        String filePath,
        int methodCount,
        int inheritanceDepth,
        int compositeScore
) {

    public Map<String, Object> toMap() {
        return Map.of(
                "classFqn", classFqn,
                "filePath", filePath,
                "methodCount", methodCount,
                "inheritanceDepth", inheritanceDepth,
                "compositeScore", compositeScore
        );
    }

    /** Simple weighted formula: methods count twice as much as inheritance depth. */
    public static int score(int methodCount, int inheritanceDepth) {
        return (methodCount * 2) + (inheritanceDepth * 3);
    }
}
