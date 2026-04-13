package com.prodbuddy.tools.kubectl;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class KubectlOperationGuardTest {

    @Test
    void shouldAllowReadOnlyCommands() {
        KubectlOperationGuard guard = new KubectlOperationGuard();

        boolean allowed = guard.isAllowed("get", List.of("kubectl", "get", "pods", "-A"));

        Assertions.assertTrue(allowed);
    }

    @Test
    void shouldRejectDestructiveCommands() {
        KubectlOperationGuard guard = new KubectlOperationGuard();

        boolean allowed = guard.isAllowed("raw", List.of("kubectl", "delete", "pod", "demo"));

        Assertions.assertFalse(allowed);
    }
}
