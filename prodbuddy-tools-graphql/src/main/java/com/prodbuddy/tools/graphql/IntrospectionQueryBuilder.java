package com.prodbuddy.tools.graphql;

import com.prodbuddy.core.system.QueryService;

import java.util.Map;

/** Utility for building GraphQL introspection queries. */
public final class IntrospectionQueryBuilder {

    /** Query service. */
    private final QueryService queryService;

    /** Default constructor. */
    public IntrospectionQueryBuilder() {
        this(new QueryService());
    }

    /**
     * Constructor with custom QueryService.
     * @param qs Query service.
     */
    public IntrospectionQueryBuilder(final QueryService qs) {
        this.queryService = qs;
    }

    /**
     * Gets the full introspection query.
     * @return Query string.
     */
    public String getFullIntrospectionQuery() {
        if (queryService.exists("graphql/introspection_full.graphql")) {
            return queryService.render("graphql/introspection_full.graphql",
                    Map.of());
        }
        return getFullIntrospectionQueryStatic();
    }

    /**
     * Gets static full introspection query.
     * @return Query string.
     */
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
            return queryService.render("graphql/operations_summary.graphql",
                    Map.of());
        }
        return getOperationsSummaryQueryStatic();
    }

    /**
     * Gets static operations summary query.
     * @return Query string.
     */
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
