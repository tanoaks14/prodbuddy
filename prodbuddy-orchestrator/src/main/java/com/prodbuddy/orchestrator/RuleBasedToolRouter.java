package com.prodbuddy.orchestrator;

import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolRouter;
import com.prodbuddy.observation.SequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;

import java.util.Optional;

public final class RuleBasedToolRouter implements ToolRouter {

    /**
     * Logger for sequence events.
     */
    private final SequenceLogger seqLog;

    /**
     * Default constructor.
     */
    public RuleBasedToolRouter() {
        this.seqLog = com.prodbuddy.observation.ObservationContext.getLogger();
    }

    @Override
    public Optional<String> route(final ToolRequest request) {
        String intent = request.intent().toLowerCase();
        seqLog.logSequence("ToolRouter", "RuleEngine",
            "route", "Matching intent: " + intent);
        Optional<String> matched = evaluateRoute(intent);
        if (matched.isPresent()) {
            seqLog.logSequence("RuleEngine", "ToolRouter",
                "route", "Matched " + matched.get());
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
        Optional<String> res = evaluateDevRoute(intent);
        if (res.isPresent()) {
            return res;
        }
        if (isObservationIntent(intent)) {
            return Optional.of("observation");
        }
        return evaluateUtilityRoute(intent);
    }

    private Optional<String> evaluateDevRoute(final String intent) {
        if (isCodeIntent(intent)) {
            return Optional.of("codecontext");
        }
        if (isKubectlIntent(intent)) {
            return Optional.of("kubectl");
        }
        if (intent.contains("git") || intent.contains("diff")) {
            return Optional.of("git");
        }
        return Optional.empty();
    }

    private Optional<String> evaluateUtilityRoute(final String intent) {
        Optional<String> apiRes = evaluateApiRoute(intent);
        if (apiRes.isPresent()) {
            return apiRes;
        }
        return evaluateAgentRoute(intent);
    }

    private Optional<String> evaluateApiRoute(final String intent) {
        if (isHttpIntent(intent)) {
            return Optional.of("http");
        }
        if (isGraphQLIntent(intent)) {
            return Optional.of("graphql");
        }
        if (intent.contains("json") || intent.contains("parse")) {
            return Optional.of("json");
        }
        return Optional.empty();
    }

    private Optional<String> evaluateAgentRoute(final String intent) {
        if (intent.contains("agent") || intent.contains("think")) {
            return Optional.of("agent");
        }
        if (intent.contains("date") || intent.contains("time")) {
            return Optional.of("datetime");
        }
        if (intent.contains("ask") || intent.contains("interactive")) {
            return Optional.of("interactive");
        }
        if (intent.contains("recipe")) {
            return Optional.of("recipe");
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

    private boolean isGraphQLIntent(final String intent) {
        return intent.contains("graphql") || intent.contains("gql");
    }

    private boolean isObservationIntent(final String intent) {
        return intent.contains("observation") || intent.contains("trace") || intent.contains("mermaid");
    }
}
