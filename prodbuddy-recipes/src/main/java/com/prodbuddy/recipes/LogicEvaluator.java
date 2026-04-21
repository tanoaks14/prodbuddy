package com.prodbuddy.recipes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple framework-like DSL evaluator for recipe conditions.
 * Supports: ==, !=, contains, &&, ||, !
 */
public final class LogicEvaluator {

    private static final Pattern COMPARISON = Pattern.compile("(.*?)\\s*(==|!=|contains)\\s*['\"]?(.*?)['\"]?\\s*$");

    public boolean evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        String trimmed = expression.trim();
        if (trimmed.contains("||")) {
            return handleOr(trimmed);
        }
        if (trimmed.contains("&&")) {
            return handleAnd(trimmed);
        }
        if (trimmed.startsWith("!")) {
            return !evaluate(trimmed.substring(1));
        }
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        return handleComparison(trimmed);
    }

    private boolean handleOr(String trimmed) {
        String[] parts = trimmed.split("\\|\\|");
        for (String part : parts) {
            if (evaluate(part)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleAnd(String trimmed) {
        String[] parts = trimmed.split("&&");
        for (String part : parts) {
            if (!evaluate(part)) {
                return false;
            }
        }
        return true;
    }

    private boolean handleComparison(String trimmed) {
        Matcher m = COMPARISON.matcher(trimmed);
        if (m.find()) {
            String left = m.group(1).trim();
            String op = m.group(2);
            String right = m.group(3).trim();

            return switch (op) {
                case "==" ->
                    left.equals(right);
                case "!=" ->
                    !left.equals(right);
                case "contains" ->
                    left.contains(right);
                default ->
                    false;
            };
        }
        return !trimmed.isEmpty();
    }
}
