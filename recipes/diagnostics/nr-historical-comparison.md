---
name: nr-historical-comparison
description: Compare current dashboard data with historical baselines (Week-over-Week)
tags: [newrelic, diagnostics, historical, comparison]
---

## setup-dashboard
tool: recipe
operation: include
path: "../common/nr-setup.md"

## get-dashboard-pages
tool: newrelic
operation: get_dashboard
guid: "${select-dashboard.guid}"

## extract-page-list
tool: json
operation: extract
data: "${get-dashboard-pages.body}"
paths:
  list: "data.actor.entity.pages"

## select-page
tool: interactive
operation: select
data: "${extract-page-list.list}"
prompt: "Select the specific PAGE you want to analyze:"

## ask-comparison
tool: interactive
operation: ask
prompt: "Specify the historical baseline to compare with (e.g., '1 week ago', '24 hours ago', or leave empty for none):"

## get-dashboard-data
tool: newrelic
operation: get_dashboard_data
guid: "${select-page.guid}"
compareWith: "${ask-comparison.answer}"

## analyze-telemetry
tool: agent
operation: think
prompt: |
  You are an Expert SRE performing a historical comparison audit. 
  
  CURRENT GOAL:
  Compare the current data from "${select-page.name}" with the baseline from "${ask-comparison.answer}".
  
  TELEMETRY SOURCE:
  ${get-dashboard-data.results}

  ANALYSIS PROTOCOL:
  1. BASELINE COMPARISON: For each widget, compare the 'current' series with the 'previous' series. 
  2. ANOMALY DETECTION: Identify if the current metrics are significantly higher (or lower) than the historical baseline.
  3. IMPACT ASSESSMENT: If there is a deviation (e.g., "Latency is 20% higher than last week"), assess if it aligns with a known event or indicates a new issue.
  4. QUANTIFICATION: Use specific numbers from the comparison (e.g., "Error rate is 2% today vs 0.5% last week").

  VERDICT:
  - Is the system performing better, worse, or the same as the historical baseline?
  - Identify the top 3 anomalies found in the comparison.
