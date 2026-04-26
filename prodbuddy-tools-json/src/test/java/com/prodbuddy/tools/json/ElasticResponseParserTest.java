package com.prodbuddy.tools.json;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ElasticResponseParserTest {

    @Test
    public void testExtractKeyLocationFromElasticResponse() {
        JsonAnalyzer analyzer = new JsonAnalyzer();
        
        String elasticResponse = """
        {
          "took": 5,
          "timed_out": false,
          "hits": {
            "total": { "value": 1, "relation": "eq" },
            "hits": [
              {
                "_index": "logs",
                "_source": {
                  "message": "Error in module",
                  "kel_location": "/app/data/keys/prod.pem",
                  "severity": "ERROR"
                }
              }
            ]
          }
        }
        """;
        
        // Define path to extract
        Map<String, String> paths = Map.of(
            "location", "hits.hits[0]._source.kel_location"
        );
        
        Map<String, Object> results = analyzer.extract(elasticResponse, paths, null);
        
        assertEquals("/app/data/keys/prod.pem", results.get("location"));
    }
}
