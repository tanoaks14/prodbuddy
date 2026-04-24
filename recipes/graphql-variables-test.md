---
name: graphql-variables-test
description: Test GraphQL queries with variables using common setup
tags: [graphql, test, include]
---

## setup
tool: recipe
operation: include
path: "./common/graphql-setup.md"

## fetch-alert
tool: graphql
operation: query
url: "${ask-graphql-url.answer}"
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

## verify
tool: agent
operation: think
prompt: "The alert retrieved via variable is: ${fetch-alert.data}"
