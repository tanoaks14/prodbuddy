package com.prodbuddy.tools.graphql;

/** Utility for building GraphQL introspection queries. */
public final class IntrospectionQueryBuilder {

    private IntrospectionQueryBuilder() { }

    /**
     * Gets the full introspection query.
     * @return Query string.
     */
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

    /**
     * Gets the operations summary query.
     * @return Query string.
     */
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
