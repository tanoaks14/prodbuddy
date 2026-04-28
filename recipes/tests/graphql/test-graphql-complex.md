---
name: test-graphql-complex
description: Test complex GraphQL input objects and lists
tags: [test]
---

## define-filters
tool: agent
operation: think
prompt: |
  Return a JSON object that defines a continent filter.
  The structure should be:
  {
    "continent_code": "AS",
    "country_limit": 5
  }
  Return ONLY the JSON.

## parse-filters
tool: json
operation: extract
data: "${define-filters.opinion}"
paths:
  continent: "continent_code"
  limit: "country_limit"

## fetch-filtered-countries
tool: graphql
operation: query
url: "https://countries.trevorblades.com/"
query: |
  query getFiltered($filter: CountryFilterInput) {
    countries(filter: $filter) {
      name
      code
      emoji
    }
  }
variables:
  filter:
    continent:
      eq: "${parse-filters.continent}"

## verify-results
tool: agent
operation: think
prompt: |
  I searched for countries in continent ${parse-filters.continent}.
  The result contains ${fetch-filtered-countries.data.data.countries[0].name} and others.
  Does this look correct?
data: "${fetch-filtered-countries.data}"
