---
name: observation-demo
description: Demonstrates the new sequence tracing and Mermaid diagram generation
tags: [test, mermaid, observation]
---

## start-trace
tool: observation
operation: clear

## ask-app
tool: interactive
operation: ask
prompt: "Which application should I look up in New Relic to start the trace?"

## find-app
tool: newrelic
operation: list_apps
name: "${ask-app.answer}"

## get-trace
tool: observation
operation: mermaid

## show-diagram
tool: agent
operation: think
prompt: |
  I have completed the New Relic lookup. Here is the visual execution trace:

  ```mermaid
  ${get-trace.mermaid}
  ```
  
  SUMMARY:
  The trace above was generated dynamically by the `observation` tool by capturing the interactions between the `interactive`, `newrelic`, and `observation` components.
