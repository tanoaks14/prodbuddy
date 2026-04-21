---
name: dashboard-diagnosis
description: Diagnose incidents by first importing New Relic dashboard context to understand monitored metrics and thresholds.
tags: [diagnostic, dashboard, contextual]
---

## step-1-import-dashboard
tool: newrelic
operation: get_dashboard
guid: ${DASHBOARD_GUID}

## step-2-agent-dashboard-analysis
tool: agent
operation: think
prompt: |
  Analyze the dashboard configuration. Identify the key metrics being monitored and their likely thresholds or normal ranges.
  Based on this, what kind of anomalies should we look for in the current telemetry?
dashboard_context: ${step-1-import-dashboard.body}

## step-3-newrelic-errors
tool: newrelic
operation: query_metrics
scenario: errors
accountId: ${NEWRELIC_ACCOUNT_ID}
timeWindowMinutes: ${DIAGNOSTIC_WINDOW}

## step-4-agent-metrics-opinion
tool: agent
operation: think
prompt: |
  Correlate the observed metrics from New Relic with the dashboard context provided in Step 2.
  Does the current error spike align with any critical widgets or alerts defined in the dashboard?
nr_response: ${step-3-newrelic-errors.summary}
dashboard_opinion: ${step-2-agent-dashboard-analysis.opinion}

## step-5-splunk-logs
tool: splunk
operation: oneshot
search: index=* "ERROR" OR "Exception" OR "timeout"
earliestTime: -${DIAGNOSTIC_WINDOW}m

## step-6-agent-logs-opinion
tool: agent
operation: think
prompt: Analyze these Splunk logs. Output ONLY the simple Java class name (e.g. InventoryService) identifying the service failing.
logs: ${step-5-splunk-logs.body}

## step-7-code-search
tool: codecontext
operation: search
projectPath: ${PRODBUDDY_PROJECT_PATH}
query: ${step-6-agent-logs-opinion.opinion}
