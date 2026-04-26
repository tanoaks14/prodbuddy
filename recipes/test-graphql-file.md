---
name: test-graphql-file
description: Tests loading GraphQL query from an external file.
---

## load-from-file
tool: graphql
operation: format
validate: true
query: @file:test_query.graphql



## verify
tool: agent
operation: think
prompt: |
  I loaded a query from an external file.
  Content: ${load-from-file.formatted}
  Does it contain DashboardEntity?
