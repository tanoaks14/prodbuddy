package com.prodbuddy.tools.graphql;

import com.prodbuddy.core.system.QueryService;

import java.util.Map;

/** Utility for building GraphQL introspection queries. */
public final class IntrospectionQueryBuilder {

    private final QueryService queryService;

    public IntrospectionQueryBuilder() {
        this(new QueryService());
    }

    public IntrospectionQueryBuilder(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Gets the full introspection query.
     * @return Query string.
     */
    public String getFullIntrospectionQuery() {
        if (queryService.exists("graphql/introspection_full.graphql")) {
            return queryService.render("graphql/introspection_full.graphql", Map.of());
        }
        return getFullIntrospectionQueryStatic();
    }

    public static String getFullIntrospectionQueryStatic() {
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
    public String getOperationsSummaryQuery() {
        if (queryService.exists("graphql/operations_summary.graphql")) {
            return queryService.render("graphql/operations_summary.graphql", Map.of());
        }
        return getOperationsSummaryQueryStatic();
    }

    public static String getOperationsSummaryQueryStatic() {
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
