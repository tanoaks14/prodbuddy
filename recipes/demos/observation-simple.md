---
name: observation-simple
description: Simple Mermaid demonstration without external cloud dependencies
tags: [test, mermaid, observation, simple]
---

## init-session

tool: observation
operation: clear

## get-current-time

tool: datetime
operation: now

## ask-user

tool: interactive
operation: ask
prompt: "The current system time is ${get-current-time.value}. Please type a word to include in the trace:"

## simulate-processing

tool: json
operation: extract
data: "{\"status\": \"active\", \"input\": \"${ask-user.answer}\"}"
paths:
  status: "status"

## get-trace

tool: observation
operation: mermaid

## render-trace-image

tool: observation
operation: render
format: png
