from ---
name: auto-investigate-errors
description: Chained diagnostic that turns 500 errors into deep-dive code context.

---

## Step 1: Log Extraction

tool: splunk
operation: oneshot
search: search "status=500" | head 5

## Step 2: Pattern Extraction

tool: agent
operation: think
prompt: |
Extract unique error messages or stacktrace signatures from these logs:
${Step 1.result}

Return ONLY a JSON object with a field "errors" which is a list of strings.
Example: {"errors": ["NullPointerException", "DB Timeout"]}

## Step 3: Investigation Loop

foreach: ${Step 2.result.errors}
as: errorSignal
steps:

- name: Code Deep Dive
  tool: recipe
  operation: run
  name: code-deep-dive
  searchTerm: ${errorSignal}
