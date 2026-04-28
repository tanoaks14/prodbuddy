---
name: test-graphql-validation
description: Test GraphQL semantic validator rules
tags: [test]
---

## validate-graphql
tool: agent
operation: validate_recipe
recipe: |
  ---
  name: inner-bad-graphql
  ---
  
  ## missing-url
  tool: graphql
  operation: query
  query: "{ test }"
  
  ## missing-query
  tool: graphql
  operation: query
  url: "https://api.example.com"
  
  ## bad-variables
  tool: graphql
  operation: query
  url: "https://api.example.com"
  query: "query ($id: ID!) { test(id: $id) }"
  variables: "id=123" # Should be a Map
