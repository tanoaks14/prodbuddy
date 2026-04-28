# GraphQL Complex Query Test
This recipe verifies the GraphQL tool handles complex queries (11 variables, 5 levels deep) loaded from an external .graphql file.

## start-trace
tool: observation
operation: clear

## Step 1: Run Complex Query
tool: graphql
operation: query
url: "https://countries.trevorblades.com/"
noTruncate: true
query: @file:../../primitives/graphql/complex_query.graphql
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

## get-trace
tool: observation
operation: mermaid

## render-trace
tool: observation
operation: render
format: png

## show-trace
tool: agent
operation: think
prompt: |
  Execution complete. Here is the mermaid sequence trace:

  ```mermaid
  ${get-trace.mermaid}
  ```
