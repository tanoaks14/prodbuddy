package com.prodbuddy.tools.http;

import java.util.Set;

public final class HttpMethodSupport {

    private static final Set<String> SUPPORTED = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"
    );

    public boolean supports(String method) {
        return SUPPORTED.contains(method.toUpperCase());
    }
}
