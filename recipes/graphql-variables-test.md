## step-1-fetch-single-alert
tool: graphql
operation: query
url: ${GRAPHQL_TEST_URL}
variables:
  targetId: 1
query: |
  query GetAlert($targetId: ID!) {
    Alert(id: $targetId) {
      id
      message
      severity
    }
  }

## step-2-verify
tool: agent
operation: think
prompt: "The alert retrieved via variable is: ${step-1-fetch-single-alert.data}"
