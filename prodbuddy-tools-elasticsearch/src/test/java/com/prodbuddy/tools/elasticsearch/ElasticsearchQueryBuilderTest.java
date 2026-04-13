package com.prodbuddy.tools.elasticsearch;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ElasticsearchQueryBuilderTest {

    @Test
    void shouldBuildFromQueryString() {
        ElasticsearchQueryBuilder builder = new ElasticsearchQueryBuilder();

        String body = builder.fromPayload(Map.of("queryString", "service:checkout AND error", "size", 25));

        Assertions.assertTrue(body.contains("query_string"));
        Assertions.assertTrue(body.contains("service:checkout AND error"));
        Assertions.assertTrue(body.contains("\"size\":25"));
    }

    @Test
    void shouldUseRawBodyWhenProvided() {
        ElasticsearchQueryBuilder builder = new ElasticsearchQueryBuilder();

        String body = builder.fromPayload(Map.of("body", "{\"query\":{\"term\":{\"level\":\"error\"}}}"));

        Assertions.assertEquals("{\"query\":{\"term\":{\"level\":\"error\"}}}", body);
    }

    @Test
    void shouldSerializeQueryDslMap() {
        ElasticsearchQueryBuilder builder = new ElasticsearchQueryBuilder();

        String body = builder.fromPayload(Map.of("queryDsl", Map.of("query", Map.of("match_all", Map.of()))));

        Assertions.assertTrue(body.contains("match_all"));
    }
}
