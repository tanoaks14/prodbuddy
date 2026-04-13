package com.prodbuddy.tools.kubectl;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class KubectlCommandBuilderTest {

    @Test
    void shouldBuildWithArgsAndFlags() {
        KubectlCommandBuilder builder = new KubectlCommandBuilder();

        List<String> command = builder.build(
                "get",
                Map.of(
                        "resource", "pods",
                        "args", List.of("-A"),
                        "flags", Map.of("selector", "app=checkout", "watch", true)
                ),
                "default"
        );

        Assertions.assertTrue(command.contains("kubectl"));
        Assertions.assertTrue(command.contains("get"));
        Assertions.assertTrue(command.contains("pods"));
        Assertions.assertTrue(command.contains("--selector"));
        Assertions.assertTrue(command.contains("app=checkout"));
        Assertions.assertTrue(command.contains("--watch"));
    }

    @Test
    void shouldParseQuotedRawCommand() {
        KubectlCommandBuilder builder = new KubectlCommandBuilder();

        List<String> command = builder.build(
                "raw",
                Map.of("command", "kubectl get pods --selector \"app=checkout api\""),
                "default"
        );

        Assertions.assertEquals("kubectl", command.get(0));
        Assertions.assertEquals("get", command.get(1));
        Assertions.assertEquals("pods", command.get(2));
        Assertions.assertEquals("--selector", command.get(3));
        Assertions.assertEquals("app=checkout api", command.get(4));
    }
}
