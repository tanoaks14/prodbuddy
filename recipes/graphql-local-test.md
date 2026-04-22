## step-1-fetch-alerts
tool: graphql
operation: query
url: ${GRAPHQL_TEST_URL}
query: |
  {
    allAlerts {
      id
      message
      severity
    }
  }

## step-2-analyze-alert
tool: agent
operation: think
prompt: "Analyze these GraphQL alerts: ${step-1-fetch-alerts.data}"
