---
name: conditional-test
description: Verifies conditional logic and interactive input.
tags: [test, dsl]
---

## step-ask
tool: system
operation: ask
question: "Do you want to run the secret step? (type 'yes' or 'no')"

## step-secret
tool: agent
operation: think
condition: ${step-ask.answer} == "yes"
prompt: "The user said yes! Tell them a secret about AI."

## step-skip
tool: agent
operation: think
condition: ${step-ask.answer} != "yes"
prompt: "The user said no. Tell them why we are skipping the secret."
