package com.prodbuddy.orchestrator;

import java.util.Optional;

import com.prodbuddy.core.tool.ToolRequest;
import com.prodbuddy.core.tool.ToolRouter;

public final class RuleBasedToolRouter implements ToolRouter {

    @Override
    public Optional<String> route(ToolRequest request) {
        String intent = request.intent().toLowerCase();
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
        return Optional.empty();
    }

    private boolean isPdfIntent(String intent) {
        return intent.contains("pdf");
    }

    private boolean isElasticIntent(String intent) {
        return intent.contains("elastic");
    }

    private boolean isNewRelicIntent(String intent) {
        return intent.contains("newrelic") || intent.contains("new relic") || intent.contains("nrql");
    }

    private boolean isSplunkIntent(String intent) {
        return intent.contains("splunk");
    }

    private boolean isHttpIntent(String intent) {
        return intent.contains("http") || intent.contains("api");
    }

    private boolean isSystemIntent(String intent) {
        return intent.contains("system") || intent.contains("catalog") || intent.contains("discovery");
    }

    private boolean isCodeIntent(String intent) {
        return intent.contains("code") || intent.contains("context") || intent.contains("repo");
    }

    private boolean isKubectlIntent(String intent) {
        return intent.contains("kubectl") || intent.contains("k8s") || intent.contains("kubernetes");
    }
}
