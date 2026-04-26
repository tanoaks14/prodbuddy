package com.prodbuddy.tools.graphql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphQLClientTest {

    private GraphQLClient client;
    private HttpRequest.Builder builder;

    @BeforeEach
    void setUp() {
        client = new GraphQLClient();
        builder = HttpRequest.newBuilder().uri(URI.create("http://test"));
    }

    @Test
    void testApplyAuthBearer() {
        Map<String, Object> auth = Map.of("mode", "bearer", "token", "my-token");
        client.applyAuth(builder, auth);
        HttpRequest req = builder.build();
        assertEquals("Bearer my-token", req.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void testApplyAuthBasic() {
        Map<String, Object> auth = Map.of(
                "mode", "basic",
                "username", "user",
                "password", "pass"
        );
        client.applyAuth(builder, auth);
        HttpRequest req = builder.build();
        
        String expected = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, req.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void testApplyAuthCookie() {
        Map<String, Object> auth = Map.of("mode", "cookie", "token", "session=123");
        client.applyAuth(builder, auth);
        HttpRequest req = builder.build();
        assertEquals("session=123", req.headers().firstValue("Cookie").orElse(null));
    }

    @Test
    void testApplyAuthCustomHeader() {
        Map<String, Object> auth = Map.of(
                "mode", "header",
                "headerName", "X-Custom",
                "token", "custom-val"
        );
        client.applyAuth(builder, auth);
        HttpRequest req = builder.build();
        assertEquals("custom-val", req.headers().firstValue("X-Custom").orElse(null));
    }
}
