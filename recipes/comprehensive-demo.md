---
name: comprehensive-demo
description: A full-feature test analyzing public APIs and simulating enterprise tool responses (Elastic, Splunk, New Relic) to demonstrate the AI diagnostic loop's failure analysis capabilities.
tags: [demo, http, elastic, splunk, newrelic, ai-analysis]
---

## step-1-http-check
tool: http
operation: get
url: https://jsonplaceholder.typicode.com/users
authEnabled: false

## step-2-validate-http
tool: json
operation: search
data: ${step-1-http-check.result.body}
key: email

## step-3-elastic-mock
tool: elasticsearch
operation: search
endpoint: _search
method: POST
body: '{"query": {"match_all": {}}}'
authEnabled: false
# We point this at a fake local port to trigger an intentional connection refused error.
# The AI will diagnose this infrastructure failure!

## step-4-splunk-sim
tool: splunk
operation: search
search: index=main error
authEnabled: false
# Intentionally triggering an HTTP 404 or auth failure against a missing Splunk cluster

## step-5-newrelic-sim
tool: newrelic
operation: query
scenario: default
metric: Transaction
timeWindowMinutes: 5
# No New Relic API key means this will reliably fail and be pushed to the LLM Context.

## step-6-system-catalog
tool: catalog
operation: list
