---
name: test-graphql-variables
description: Verify GraphQL variable resolution with a public API
tags: [test]
---

## prepare-data
tool: agent
operation: think
prompt: "Return a JSON object with a single key 'country_code' and value 'IN'. Return ONLY the JSON."

## extract-code
tool: json
operation: extract
data: "${prepare-data.opinion}"
paths:
  code: "country_code"

## fetch-country
tool: graphql
operation: query
url: "https://countries.trevorblades.com/"
query: |
  query getCountry($code: ID!) {
    country(code: $code) {
      name
      capital
      emoji
    }
  }
variables:
  code: "${extract-code.code}"

## final-check
tool: agent
operation: think
prompt: "The country found was ${fetch-country.data.data.country.name}. Does this match the code ${extract-code.code}?"
data: "${fetch-country.data}"
