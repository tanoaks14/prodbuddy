---
name: nr-dashboard-snapshot
description: Interactively generate a New Relic dashboard snapshot and analyze it with Multimodal LLM
tags: [interactive, newrelic, multimodal, agent]
---

## ask-dashboard-name

tool: interactive
operation: ask
prompt: "What is the name of the New Relic dashboard you are looking for? (Partial names work)"
cacheKey: "nr-dashboard-name"
autoSelect: true

## search-dashboards

tool: newrelic
operation: list_dashboards
name: "${ask-dashboard-name.answer}"

## extract-dashboard-list

tool: json
operation: extract
data: "${search-dashboards.body}"
paths:
list: "data.actor.entitySearch.results.entities"

## select-dashboard

tool: interactive
operation: select
data: "${extract-dashboard-list.list}"
prompt: "Select a dashboard from the list above:"
cacheKey: "nr-dashboard-guid"
autoSelect: true

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
prompt: "Select the specific PAGE you want to snapshot:"
cacheKey: "nr-page-guid"
autoSelect: true

## generate-snapshot

tool: newrelic
operation: snapshot
guid: "${select-page.guid}"
format: PNG

## get-dashboard-data

tool: newrelic
operation: get_dashboard_data
guid: "${select-dashboard.guid}"
pageGuid: "${select-page.guid}"
duration: 15

# Use the 'variables' map to resolve placeholders in template dashboards (e.g., {{appName}})

variables:
appName: "OrderService"
http.method.filter: "POST"
uri.filter: "%"
http.status.filter: "200"

## analyze-telemetry

tool: agent
operation: think
prompt: |
You are an Expert SRE performing a diagnostic audit.
Analyze the raw telemetry data extracted from the "${select-page.name}" dashboard.

DATA SOURCES:

1. Visual Analysis (from snapshot): ${analyze-snapshot.opinion}
2. Raw Widget Data (JSON): ${get-dashboard-data.results}

ANALYSIS PROTOCOL:

1. TREND DETECTION: Scan the 'data' series for each widget in the JSON. Identify sudden spikes, flatlines, or steady increases.
2. THRESHOLD AUDIT: Look for values exceeding critical thresholds (e.g., Error Rate > 5%, p95 Latency > 1s, CPU > 80%).
3. CORRELATION: Check if latency increases in one widget align with error rate spikes or throughput changes in others.
4. QUANTIFICATION: Be precise. Mention specific peak values and timestamps found in the JSON (e.g., "Error rate peaked at 8.4%").

VERDICT:

- Is the system experiencing a "Hard Failure" (spikes in errors) or "Degradation" (slow latency/saturation)?
- Based on the widget titles and data correlation, what is the most likely root cause?

Provide your analysis in a concise, action-oriented format.

## summarize-result

tool: agent
operation: think
prompt: |
Status Check:

- Dashboard: ${select-dashboard.name}
- Page: ${select-page.name}
- Snapshot Capture: ${generate-snapshot.success}
- Data Extraction: ${get-dashboard-data.success}

SRE Diagnostic Insight:
${analyze-telemetry.opinion}

Summary Note:
The analysis combined visual trend detection with raw NRQL result extraction (Top 10 widgets) to ensure High-Fidelity diagnostic accuracy.
