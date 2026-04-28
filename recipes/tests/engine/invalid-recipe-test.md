---
name: invalid-recipe-test
description: Verifies that the validator catches semantic and reference errors.
---

## step-1-validate
tool: agent
operation: validate_recipe
recipe: |
  ## step-A
  tool: splunk-wrong
  operation: login

  ## step-B
  tool: splunk
  operation: oneshot
  cookie: ${nonExistentStep.cookie}
