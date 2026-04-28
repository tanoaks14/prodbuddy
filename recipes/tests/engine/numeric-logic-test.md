---
name: numeric-logic-test
description: Verifies the upgraded logic engine with numeric threshold comparisons.
tags: [test, engine]
---

## step-threshold-low
tool: agent
operation: think
prompt: |
  Return a JSON object with a single field 'value' set to 5.
  Respond ONLY with the JSON.

## step-parse-low
tool: json
operation: extract
data: ${step-threshold-low.opinion}
regex:
  val: (\d+)

## step-check-low
condition: ${step-parse-low.val} < 10
tool: agent
operation: think
prompt: "Condition 5 < 10 PASSED"

## step-threshold-high
tool: agent
operation: think
prompt: |
  Return a JSON object with a single field 'value' set to 25.
  Respond ONLY with the JSON.

## step-parse-high
tool: json
operation: extract
data: ${step-threshold-high.opinion}
regex:
  val: (\d+)

## step-check-high
condition: ${step-parse-high.val} >= 20
tool: agent
operation: think
prompt: "Condition 25 >= 20 PASSED"
