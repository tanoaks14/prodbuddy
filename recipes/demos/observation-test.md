---
name: observation-test
description: Automated test for observation styling
---

## init
tool: observation
operation: clear

## step1
tool: datetime
operation: now

## step2
tool: json
operation: extract
data: "{\"status\": \"ok\", \"time\": \"${step1.value}\"}"
paths:
  status: "status"

## mermaid
tool: observation
operation: mermaid

## render
tool: observation
operation: render
format: png
