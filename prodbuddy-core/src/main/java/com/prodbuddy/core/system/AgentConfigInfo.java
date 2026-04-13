package com.prodbuddy.core.system;

public record AgentConfigInfo(
        String provider,
        String model,
        String baseUrl,
        boolean enabled,
        boolean authEnabled
        ) {

}
