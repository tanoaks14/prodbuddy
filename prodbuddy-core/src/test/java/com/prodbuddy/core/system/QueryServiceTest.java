package com.prodbuddy.core.system;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class QueryServiceTest {

    private final QueryService queryService = new QueryService();

    @Test
    public void testRenderAndInterpolate() {
        String result = queryService.render("test/hello.txt", Map.of("name", "Alice", "place", "Wonderland"));
        assertEquals("Hello Alice! Welcome to Wonderland.", result.trim());
    }

    @Test
    public void testMissingVariables() {
        String result = queryService.render("test/hello.txt", Map.of("name", "Bob"));
        assertEquals("Hello Bob! Welcome to ${place}.", result.trim());
    }

    @Test
    public void testResourceNotFound() {
        assertThrows(RuntimeException.class, () -> {
            queryService.render("test/missing.txt", Map.of());
        });
    }

    @Test
    public void testExists() {
        assertTrue(queryService.exists("test/hello.txt"));
        assertFalse(queryService.exists("test/missing.txt"));
    }
}
