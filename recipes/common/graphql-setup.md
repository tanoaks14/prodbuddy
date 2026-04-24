---
name: graphql-common-setup
description: Shared steps for GraphQL endpoint setup and introspection
tags: [common, graphql]
---

## ask-graphql-url
tool: interactive
operation: ask
prompt: "Enter the GraphQL endpoint URL:"

## introspect-schema
tool: graphql
operation: introspect
url: "${ask-graphql-url.answer}"
stopOnFailure: true
