package com.prodbuddy.tools.graphql;

public final class IntrospectionQueryBuilder {
    
    public static String getFullIntrospectionQuery() {
        return """
        query IntrospectionQuery {
          __schema {
            queryType { name }
            mutationType { name }
            types {
              name
              kind
              description
              fields {
                name
                description
                args {
                  name
                  description
                  type {
                    name
                    kind
                  }
                }
              }
            }
          }
        }
        """;
    }

    public static String getOperationsSummaryQuery() {
        return """
        {
          __schema {
            queryType {
              fields {
                name
                description
              }
            }
            mutationType {
              fields {
                name
                description
              }
            }
          }
        }
        """;
    }
}
