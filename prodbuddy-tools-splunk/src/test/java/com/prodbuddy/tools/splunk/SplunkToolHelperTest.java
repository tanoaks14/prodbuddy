package com.prodbuddy.tools.splunk;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SplunkToolHelperTest {

    private static final Pattern JSON_PATTERN = Pattern.compile("\"sessionKey\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern XML_PATTERN = Pattern.compile("<sessionKey>([^<]+)</sessionKey>");

    @Test
    void testExtractSessionKeyJson() {
        String body = "{\"sessionKey\":\"abc-123-xyz\"}";
        assertEquals("abc-123-xyz", SplunkAuthHelper.extractSessionKey(body, JSON_PATTERN, XML_PATTERN));
    }

    @Test
    void testExtractSessionKeyXml() {
        String body = "<response><sessionKey>xml-key-456</sessionKey></response>";
        assertEquals("xml-key-456", SplunkAuthHelper.extractSessionKey(body, JSON_PATTERN, XML_PATTERN));
    }

    @Test
    void testExtractPort() {
        assertEquals("8089", SplunkToolHelper.extractPort("https://localhost:8089"));
        assertEquals("443", SplunkToolHelper.extractPort("https://splunk.example.com:443"));
        assertEquals("8089", SplunkToolHelper.extractPort("https://splunk.example.com")); // Default
    }

    @Test
    void testTruncate() {
        String longString = "A".repeat(1000);
        String truncated = SplunkToolHelper.truncate(longString);
        assertEquals(600, truncated.length());
        
        String shortString = "Hello";
        assertEquals("Hello", SplunkToolHelper.truncate(shortString));
    }
}
