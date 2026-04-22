---
name: splunk-advanced-cookie-flow
description: Detailed demonstration of Splunk Web browser-like flow using cookies and async jobs
tags: [splunk, advanced, cookie]
---

## 1. splunk-login
tool: splunk
operation: login
authMode: user
# Uses SPLUNK_USERNAME and SPLUNK_PASSWORD from environment

## 2. create-search-job
tool: splunk
operation: search
authMode: cookie
cookie: ${splunk-login.cookie}
# Custom path matching browser-like proxy if needed
# path: "en-gb/splunkd/_raw/serviceNS/${SPLUNK_USERNAME}/search/v2/job"
# searchKey: "custom.search"
search: |
  index=main status=500
  | stats count by host, serviceName
params:
  exec_mode: "normal"
  earliest_time: "-1h"
  latest_time: "now"

## 3. check-job-status
tool: agent
operation: think
prompt: "The Splunk Search Job ID (SID) is ${create-search-job.sid}. We will now fetch the results."

## 4. fetch-results
tool: splunk
operation: results
authMode: cookie
cookie: ${splunk-login.cookie}
sid: ${create-search-job.sid}
outputMode: "json"
# Tool automatically uses GET for 'results' operation

## 5. analyze-telemetry
tool: agent
operation: think
condition: ${fetch-results.status} == 200
prompt: |
  Analyze these Splunk error counts:
  ${fetch-results.body}
  
  Recommend if we need to check Kubernetes logs for the top failing host.

## 6. conditional-k8s-check
tool: kubectl
operation: get
condition: ${analyze-telemetry.opinion} contains 'KUBERNETES'
resource: pods
flags:
  all-namespaces: true
