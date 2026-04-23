package com.prodbuddy.tools.http;

import java.util.Set;

/** Guard for supported HTTP methods. */
public final class HttpMethodSupport {

    /** Supported methods. */
    private static final Set<String> SUPPORTED = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"
    );

    /**
     * Checks if a method is supported.
     * @param method HTTP method name.
     * @return true if supported.
     */
    public boolean supports(final String method) {
        return SUPPORTED.contains(method.toUpperCase());
    }
}
