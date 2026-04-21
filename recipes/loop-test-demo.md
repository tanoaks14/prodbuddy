---
name: loop-test-demo
description: Demonstrates nested loops and lexical variable scoping in recipes.
tags: [test, loop]
---

## step-preamble
tool: agent
operation: think
prompt: "Preparing to run the loop..."

## step-multi-check
foreach: pod-a,pod-b,pod-c
as: pod
stopOnFailure: true
steps:
  - name: check-pod
    tool: agent
    operation: think
    params:
      prompt: "Analyzing pod: ${pod}. Status looks good."
