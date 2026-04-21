package com.prodbuddy.recipes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple framework-like DSL evaluator for recipe conditions.
 * Supports: ==, !=, contains, &&, ||, !
 */
public final class LogicEvaluator {

    private static final Pattern COMPARISON = Pattern.compile("(.*?)\\s*(==|!=|contains|>=|<=|>|<)\\s*['\"]?(.*?)['\"]?\\s*$");

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
        return switch (trimmed.toLowerCase()) {
            case "true" -> true;
            case "false" -> false;
            default -> handleComparison(trimmed);
        };
    }

    private boolean handleOr(String trimmed) {
        for (String part : trimmed.split("\\|\\|")) {
            if (evaluate(part)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleAnd(String trimmed) {
        for (String part : trimmed.split("&&")) {
            if (!evaluate(part)) {
                return false;
            }
        }
        return true;
    }

    private boolean handleComparison(String trimmed) {
        Matcher m = COMPARISON.matcher(trimmed);
        if (m.find()) {
            String leftStr = m.group(1).trim();
            String op = m.group(2);
            String rightStr = m.group(3).trim();

            return executeOperator(leftStr, op, rightStr);
        }
        return !trimmed.isEmpty();
    }

    private boolean executeOperator(String left, String op, String right) {
        return switch (op) {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            case "contains" -> left.contains(right);
            case ">", "<", ">=", "<=" -> compareNumeric(left, op, right);
            default -> false;
        };
    }

    private boolean compareNumeric(String left, String op, String right) {
        try {
            double l = Double.parseDouble(left);
            double r = Double.parseDouble(right);
            return switch (op) {
                case ">" -> l > r;
                case "<" -> l < r;
                case ">=" -> l >= r;
                case "<=" -> l <= r;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
