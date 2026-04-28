---
name: test-graphql-format
description: Tests the new graphql.format operation and list-based queries.
---

## format-query
tool: graphql
operation: format
validate: true
query: |
  query getFiltered($filter: CountryFilterInput) {
    countries(filter: $filter) {
      name
    }
  }


## test-validation-failure
tool: graphql
operation: format
validate: true
query: "query { unbalanced "

## run-formatted
tool: graphql
operation: query
validate: true
url: https://countries.trevorblades.com/
query: "${format-query.formatted}"
variables:
  filter:
    code:
      eq: "IN"


## verify
tool: agent
operation: think
prompt: |
  I ran a formatted query for country IN.
  Result: ${run-formatted.data.data.countries[0].name}
  Does the formatted query look correct?
