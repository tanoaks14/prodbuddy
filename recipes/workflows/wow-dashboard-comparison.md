---
name: wow-dashboard-comparison
description: Compare current dashboard telemetry with baseline from exactly one week ago to identify shifts and anomalies.
tags: [newrelic, observability, wow, performance]
---

## select-dashboard
tool: newrelic
operation: list_dashboards
name: "${DASHBOARD_NAME}"

## get-dashboard-details
tool: newrelic
operation: get_dashboard
guid: "${select-dashboard.entities[0].guid}"

## extract-pages
tool: json
operation: extract
data: "${get-dashboard-details.body}"
paths:
  pages: "data.actor.entity.pages"

## select-page
tool: interactive
operation: select
data: "${extract-pages.pages}"
prompt: "Select the dashboard page for WoW comparison:"

## fetch-comparison-data
tool: newrelic
operation: get_dashboard_data
guid: "${select-dashboard.entities[0].guid}"
pageGuid: "${select-page.guid}"
compareWith: "1 week ago"
duration: 60

## analyze-performance-shifts
tool: agent
operation: think
prompt: |
  # Week-over-Week Performance Audit
  
  **Dashboard:** ${select-dashboard.entities[0].name}
  **Page:** ${select-page.name}
  **Baseline:** Exactly 1 week ago (Week-over-Week)
  
  ## Telemetry Data
  ${fetch-comparison-data.results}
  
  ## Analysis Task
  You are a Lead Site Reliability Engineer. Analyze the provided telemetry which includes both 'current' and 'previous' (1 week ago) data points.
  
  1. **Identify Significant Shifts**: Call out any metric that has changed by >15% compared to last week.
  2. **Categorize Observations**:
     - **Degradation**: Metrics that are trending in a negative direction (e.g., higher error rate, higher latency).
     - **Improvement**: Metrics that show better performance than last week.
     - **Stability**: Critical metrics that remain consistent with the baseline.
  3. **Root Cause Hypotheses**: For any major degradation, suggest 2-3 possible reasons (e.g., new deployment, traffic spike, resource exhaustion).
  4. **Actionable Recommendations**: What should the team do next based on these observations?
  
  ## Output Format
  Provide a concise executive summary followed by a bulleted list of key observations.
