---
name: public-api-test
description: Diagnostic recipe that chains public APIs to validate orchestration, then sends all collected data to the AI for analysis.
tags: [test, http, open-api, debug, ai-analysis]
---

## get-random-user
tool: http
operation: get
url: https://jsonplaceholder.typicode.com/users/1
authEnabled: false

## assert-user-id
tool: json
operation: assert
data: ${get-random-user.result.body}
path: id
expected: 1

## check-user-todos
tool: http
operation: get
url: https://jsonplaceholder.typicode.com/users/${get-random-user.result.jsonBody.id}/todos
authEnabled: false

## search-completed-todos
tool: json
operation: search
data: ${check-user-todos.result.body}
key: completed

## get-user-posts
tool: http
operation: get
url: https://jsonplaceholder.typicode.com/users/${get-random-user.result.jsonBody.id}/posts
authEnabled: false

## count-posts
tool: json
operation: search
data: ${get-user-posts.result.body}
key: title

## poke-api-test
tool: http
operation: get
url: https://pokeapi.co/api/v2/pokemon/pikachu
authEnabled: false

## assert-pokemon-name
tool: json
operation: assert
data: ${poke-api-test.result.body}
path: name
expected: pikachu
