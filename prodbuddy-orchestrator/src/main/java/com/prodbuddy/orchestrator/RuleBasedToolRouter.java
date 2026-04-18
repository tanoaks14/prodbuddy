package com.prodbuddy.orchestrator;

import java.util.Optional;

import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolRouter;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

public final class RuleBasedToolRouter implements ToolRouter {

    private final SequenceLogger seqLog;

    public RuleBasedToolRouter() {
        this.seqLog = new Slf4jSequenceLogger(RuleBasedToolRouter.class);
    }

    @Override
    public Optional<String> route(final ToolRequest request) {
        String intent = request.intent().toLowerCase();
        seqLog.logSequence("RuleBasedToolRouter", "RuleEngine",
            "route", "Matching intent: " + intent);
        Optional<String> matched = evaluateRoute(intent);
        if (matched.isPresent() && matched.get().equals("pdf")) {
            seqLog.logSequence("RuleEngine", "RuleBasedToolRouter",
                "route", "Matched pdf");
        }
        return matched;
    }

    private Optional<String> evaluateRoute(final String intent) {
        Optional<String> coreRoute = evaluateCoreRoute(intent);
        if (coreRoute.isPresent()) {
            return coreRoute;
        }
        return evaluateIntegrationRoute(intent);
    }

    private Optional<String> evaluateCoreRoute(final String intent) {
        if (isPdfIntent(intent)) {
            return Optional.of("pdf");
        }
        if (isElasticIntent(intent)) {
            return Optional.of("elasticsearch");
        }
        if (isNewRelicIntent(intent)) {
            return Optional.of("newrelic");
        }
        if (isSplunkIntent(intent)) {
            return Optional.of("splunk");
        }
        return Optional.empty();
    }

    private Optional<String> evaluateIntegrationRoute(final String intent) {
        if (isSystemIntent(intent)) {
            return Optional.of("system");
        }
        if (isCodeIntent(intent)) {
            return Optional.of("codecontext");
        }
        if (isHttpIntent(intent)) {
            return Optional.of("http");
        }
        if (isKubectlIntent(intent)) {
            return Optional.of("kubectl");
        }
        if (intent.contains("json") || intent.contains("parse")) {
            return Optional.of("json");
        }
        return Optional.empty();
    }

    private boolean isPdfIntent(final String intent) {
        return intent.contains("pdf");
    }

    private boolean isElasticIntent(final String intent) {
        return intent.contains("elastic");
    }

    private boolean isNewRelicIntent(final String intent) {
        return intent.contains("newrelic")
            || intent.contains("new relic")
            || intent.contains("nrql");
    }

    private boolean isSplunkIntent(final String intent) {
        return intent.contains("splunk");
    }

    private boolean isHttpIntent(final String intent) {
        return intent.contains("http") || intent.contains("api");
    }

    private boolean isSystemIntent(final String intent) {
        return intent.contains("system")
            || intent.contains("catalog")
            || intent.contains("discovery");
    }

    private boolean isCodeIntent(final String intent) {
        return intent.contains("code")
            || intent.contains("context")
            || intent.contains("repo");
    }

    private boolean isKubectlIntent(final String intent) {
        return intent.contains("kubectl")
            || intent.contains("k8s")
            || intent.contains("kubernetes");
    }
}
