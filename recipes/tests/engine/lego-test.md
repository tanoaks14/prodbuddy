---
name: lego-test
description: Master recipe that calls a sub-recipe
tags: [test]
---

## call-sub
tool: recipe
operation: run
name: sub-recipe

## verify-sub
tool: agent
operation: think
prompt: |
  Sub-recipe executed ${call-sub.steps_executed} steps.
  The git status returned: ${get-git-status.statusOutput}
