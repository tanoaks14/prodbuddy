---
name: log-pattern-synthesis
description: Extracts clustered patterns and facts from raw logs.
---

## Step 1: Execute Log Search
tool: splunk
operation: oneshot
search: search "${search_query}" | cluster showcount=t countfield=cluster_count | head 5

## Step 2: Synthesis Reasoning
tool: agent
operation: think
prompt: |
  Analyze the following clustered log patterns from the search query: "${search_query}"
  
  Clusters:
  ${Step 1.result}

  Identify the top 3 most frequent error patterns. For each, provide:
  - Error Signature: (Common exception or message)
  - Frequency: (Total count across clusters)
  - Sample Context: (Partial stacktrace or log line)
  
  Format as a concise summary for a diagnostic report.
