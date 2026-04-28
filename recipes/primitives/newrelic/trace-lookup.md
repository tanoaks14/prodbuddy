---
name: trace-diagnosis
description: Verifies New Relic external services and distributed tracing operations.
tags: [test, newrelic]
---

## step-list-externals
tool: newrelic
operation: list_external_services

## step-analysis
tool: agent
operation: think
prompt: "Analyze the external services found: ${step-list-externals.body}. Are there any suspicious ones related to 'Inventory' or 'Database'?"

## step-get-trace
tool: newrelic
operation: get_trace
condition: ${step-analysis.opinion} contains 'suspicious'
traceId: "PLACEHOLDER_TRACE_ID"
