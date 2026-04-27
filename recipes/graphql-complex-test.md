# GraphQL Complex Query Test
This recipe verifies the GraphQL tool handles complex queries (11 variables, 5 levels deep) loaded from an external .graphql file.

## Step 1: Run Complex Query
tool: graphql
operation: query
url: "https://countries.trevorblades.com/"
noTruncate: true
query: @file:complex_query.graphql
variables:
  continent1: "EU"
  continent2: "AS"
  country1: "FR"
  country2: "DE"
  country3: "JP"
  lang1: "fr"
  lang2: "de"
  lang3: "ja"
  currFilter: "EUR"
  contFilter: "EU"
  codeFilter: "US"

## Step 2: Verify Results
tool: agent
operation: think
prompt: |
  Verify the GraphQL response contains data for continents EU and AS,
  countries FR, DE, JP, and languages fr, de, ja.
  Result: ${Step 1.result.data}
