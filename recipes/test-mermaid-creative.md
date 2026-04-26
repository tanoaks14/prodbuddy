---
name: test-mermaid-creative
description: Tests creative Mermaid rendering with colored actors and notes
tags: [test, mermaid]
analysis: true
---

## step-1-agent-think
tool: agent
operation: think
prompt: "Analyze the system state. We are testing Mermaid colors."

## step-2-observation-mermaid
tool: observation
operation: mermaid
logFullResponse: true
