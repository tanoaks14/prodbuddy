package com.prodbuddy.tools.kubectl;

import java.util.List;
import java.util.Set;

public final class KubectlOperationGuard {

    private static final Set<String> ALLOWED_OPERATIONS = Set.of(
            "get",
            "describe",
            "logs",
            "top",
            "version",
            "cluster-info",
            "api-resources",
            "raw",
            "command"
    );

    private static final Set<String> FORBIDDEN_VERBS = Set.of(
            "apply",
            "create",
            "delete",
            "replace",
            "patch",
            "edit",
            "scale",
            "drain",
            "cordon",
            "uncordon",
            "taint",
            "exec",
            "cp",
            "port-forward",
            "rollout"
    );

    public boolean isAllowed(String operation, List<String> command) {
        String normalizedOperation = operation == null ? "" : operation.toLowerCase();
        if (!ALLOWED_OPERATIONS.contains(normalizedOperation)) {
            return false;
        }
        return !containsForbiddenVerb(command);
    }

    private boolean containsForbiddenVerb(List<String> command) {
        for (String token : command) {
            String value = token.toLowerCase();
            if (FORBIDDEN_VERBS.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
