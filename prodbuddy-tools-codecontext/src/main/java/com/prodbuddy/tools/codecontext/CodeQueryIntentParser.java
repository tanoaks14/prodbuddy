package com.prodbuddy.tools.codecontext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodeQueryIntentParser {

    private static final Pattern ENTITY_PATTERN = Pattern.compile("[A-Z][a-zA-Z0-9_]{2,}|[a-zA-Z]+Exception|[A-Z_]{3,}");
    private final com.prodbuddy.observation.SequenceLogger seqLog;

    public CodeQueryIntentParser() {
        this.seqLog = new com.prodbuddy.observation.Slf4jSequenceLogger(CodeQueryIntentParser.class);
    }

    public CodeQueryIntent parse(String query) {
        seqLog.logSequence("CodeQueryIntentParser", "IntentParser", "parse", "Started parse flow");
        String normalized = normalize(query);
        String category = category(normalized);
        List<String> entities = entities(query == null ? "" : query);
        List<String> expanded = expandedTerms(normalized);
        seqLog.logSequence("IntentParser", "CodeQueryIntentParser", "parse", "Finalizing intent: " + category);
        return new CodeQueryIntent(category, normalized, entities, expanded, confidence(category));
    }

    private String normalize(String query) {
        seqLog.logSequence("IntentParser", "TextNormalizer", "normalize", "Normalizing query text");
        String value = query == null ? "" : query;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String category(String normalizedQuery) {
        seqLog.logSequence("IntentParser", "Categorizer", "category", "Determining context category");
        if (containsAny(normalizedQuery, "timeout", "latency", "slow", "performance", "p95", "p99", "throughput")) {
            return "performance";
        }
        if (containsAny(normalizedQuery, "error", "exception", "failed", "stacktrace", "500", "5xx", "incident")) {
            return "bug";
        }
        if (containsAny(normalizedQuery, "dependency", "import", "extends", "implements", "classpath", "module")) {
            return "dependency";
        }
        if (containsAny(normalizedQuery, "pod", "kubernetes", "kubectl", "container", "oom")) {
            return "platform";
        }
        if (containsAny(normalizedQuery, "refactor", "rename", "cleanup")) {
            return "refactor";
        }
        return "general";
    }

    private boolean containsAny(String source, String... terms) {
        for (String term : terms) {
            if (source.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private List<String> entities(String query) {
        seqLog.logSequence("IntentParser", "EntityExtractor", "entities", "Extracting domain entities");
        Matcher matcher = ENTITY_PATTERN.matcher(query);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            values.add(matcher.group());
        }
        return List.copyOf(values);
    }

    private List<String> expandedTerms(String normalizedQuery) {
        seqLog.logSequence("IntentParser", "TermExpander", "expandedTerms", "Expanding core terms");
        List<String> values = new ArrayList<>();
        if (normalizedQuery.contains("timeout")) {
            values.add("latency"); values.add("deadline"); values.add("slow");
        }
        if (normalizedQuery.contains("error") || normalizedQuery.contains("exception")) {
            values.add("failed"); values.add("stacktrace");
        }
        if (normalizedQuery.contains("payment")) {
            values.add("checkout"); values.add("order");
        }
        if (normalizedQuery.contains("oom") || normalizedQuery.contains("memory")) {
            values.add("outofmemoryerror"); values.add("heap"); values.add("gc");
        }
        if (normalizedQuery.contains("5xx") || normalizedQuery.contains("500")) {
            values.add("error"); values.add("exception"); values.add("failed");
        }
        return List.copyOf(values);
    }

    private double confidence(String category) {
        seqLog.logSequence("IntentParser", "ConfidenceCalculator", "confidence", "Scoring " + category);
        if ("general".equals(category)) {
            return 0.55;
        }
        return 0.85;
    }
}
