---
name: nr-dashboard-snapshot
description: Interactively generate a New Relic dashboard snapshot and analyze it with Multimodal LLM
tags: [interactive, newrelic, multimodal, agent]
---

## ask-dashboard-name
tool: interactive
operation: ask
prompt: "What is the name of the New Relic dashboard you are looking for? (Partial names work)"

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

## generate-snapshot
tool: newrelic
operation: snapshot
guid: "${select-page.guid}"
format: PNG

## download-image-base64
condition: "${generate-snapshot.success} == true"
tool: http
operation: download_base64
url: "${generate-snapshot.body}"
method: GET

## analyze-snapshot
condition: "${download-image-base64.success} == true"
tool: agent
operation: think
prompt: "Please analyze this New Relic dashboard snapshot. Look for error spikes, high latency, or unusual patterns."
image: "${download-image-base64.base64}"

## summarize-result
tool: agent
operation: think
prompt: |
  Status Check:
  - Dashboard: ${select-dashboard.name}
  - Page: ${select-page.name}
  - Snapshot Success: ${generate-snapshot.success}
  - Image Download: ${download-image-base64.success}
  
  Snapshot URL: ${generate-snapshot.body}
  
  Analysis Opinion:
  ${analyze-snapshot.opinion}
  
  Notes:
  1. The snapshot was captured in PNG format for better visual analysis.
  2. You can change 'format: PNG' to 'format: PDF' in the recipe if you prefer a document format.
