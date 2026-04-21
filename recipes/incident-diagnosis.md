---
name: incident-diagnosis
description: Automated end-to-end diagnostic chain from NR anomalies to Splunk logs and deep code analysis.
tags: [diagnostic, investigation, automated]
---

## step-1-newrelic-errors
tool: newrelic
operation: query_metrics
scenario: errors
accountId: ${NEWRELIC_ACCOUNT_ID}
timeWindowMinutes: ${DIAGNOSTIC_WINDOW}

## step-2-agent-metrics-opinion
tool: agent
operation: think
prompt: Based on the New Relic error metrics, which services or log patterns should we investigate in Splunk?
nr_response: ${step-1-newrelic-errors.summary}

## step-3-splunk-logs
tool: splunk
operation: oneshot
search: index=* "ERROR" OR "Exception" OR "timeout"
earliestTime: -${DIAGNOSTIC_WINDOW}m

## step-4-agent-logs-opinion
tool: agent
operation: think
prompt: Analyze these Splunk logs. Output ONLY the simple Java class name (e.g. InventoryService). Do NOT include reasoning or any other text.
logs: ${step-3-splunk-logs.body}

## step-5-code-search
tool: codecontext
operation: search
projectPath: ${PRODBUDDY_PROJECT_PATH}
query: ${step-4-agent-logs-opinion.opinion}

## step-6-agent-code-opinion
tool: agent
operation: think
prompt: |
  Evaluate the code search results. Identify the method most likely to be the root cause of the incident.
  Output ONLY the full Method ID in this exact format: Package.Class#methodName(Signature)
  Example: com.prodbuddy.test.DemoApp$IncidentHandler#handle(HttpExchange)
  If no clear method is found, output: NONE
search_results: ${step-5-code-search.matches}

## step-7-call-chain-analysis
tool: codecontext
operation: call_chain
dbPath: .prodbuddy/java-ast-db
startMethodId: ${step-6-agent-code-opinion.opinion}
direction: DOWN
maxDepth: 3
