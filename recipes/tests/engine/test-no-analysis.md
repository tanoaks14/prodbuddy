---
name: test-no-analysis
description: Testing that AI analysis is skipped when analysis: false
tags: [test, analysis]
analysis: false
---

## step-1-echo
tool: agent
operation: think
prompt: "This should NOT be followed by an AI analysis."
