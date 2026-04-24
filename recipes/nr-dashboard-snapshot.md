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

## list-dashboard-options
tool: agent
operation: think
prompt: |
  I found these dashboards matching "${ask-dashboard-name.answer}":
  ${search-dashboards.body}
  
  Please present these to the user as a clear list of (Name: GUID) so they can pick one.

## ask-dashboard-selection
tool: interactive
operation: ask
prompt: |
  Available Dashboards:
  ${list-dashboard-options.opinion}
  
  Please enter the GUID of the dashboard you want to snapshot:

## get-dashboard-pages
tool: newrelic
operation: get_dashboard
guid: "${ask-dashboard-selection.answer}"

## list-page-options
tool: agent
operation: think
prompt: |
  The dashboard "${ask-dashboard-selection.answer}" has the following pages:
  ${get-dashboard-pages.body}
  
  Please present these to the user as a clear list of (Page Name: Page GUID) so they can pick one.

## ask-page-selection
tool: interactive
operation: ask
prompt: |
  Available Pages:
  ${list-page-options.opinion}
  
  Please enter the GUID of the specific PAGE you want to snapshot:

## generate-snapshot
tool: newrelic
operation: snapshot
guid: "${ask-page-selection.answer}"

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
  - Snapshot Success: ${generate-snapshot.success}
  - Image Download: ${download-image-base64.success}
  
  Snapshot URL: ${generate-snapshot.body}
  
  Analysis Opinion:
  ${analyze-snapshot.opinion}
  
  If successful:
  1. Copy the URL from the response for your records.
  2. If you want a PNG image instead of a PDF, change '?format=PDF' to '?format=PNG' at the end of the URL.
