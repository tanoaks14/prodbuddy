package com.prodbuddy.tools.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodbuddy.core.tool.ToolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

class GraphQLToolDeepTest {

    private GraphQLTool tool;
    private ObjectMapper mapper = new ObjectMapper();

    @Mock
    private GraphQLClient client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new GraphQLTool(client);
    }

    @Test
    void testListOperationsParsing() throws Exception {
        String mockResponse = """
            {
              "data": {
                "__schema": {
                  "queryType": {
                    "fields": [
                      {"name": "getUser", "description": "Get user by ID"},
                      {"name": "listItems", "description": "List all items"}
                    ]
                  },
                  "mutationType": {
                    "fields": [
                      {"name": "createUser", "description": "Create a new user"}
                    ]
                  }
                }
              }
            }
            """;
        
        when(client.execute(any(), any(), isNull(), any())).thenReturn(mockResponse);
        
        com.prodbuddy.core.tool.ToolRequest request = new com.prodbuddy.core.tool.ToolRequest(
            "graphql", "list_operations", Map.of("url", "http://test")
        );
        ToolResponse response = tool.execute(request, null);
        
        Map<String, Object> data = (Map<String, Object>) response.data();
        List<Map<String, String>> queries = (List<Map<String, String>>) data.get("queries");
        List<Map<String, String>> mutations = (List<Map<String, String>>) data.get("mutations");
        
        assertEquals(2, queries.size());
        assertEquals("getUser", queries.get(0).get("name"));
        assertEquals("Get user by ID", queries.get(0).get("description"));
        
        assertEquals(1, mutations.size());
        assertEquals("createUser", mutations.get(0).get("name"));
    }
}
