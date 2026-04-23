---
name: splunk-browser-mimic
description: Advanced recipe mimicking browser-based Splunk Web job creation
tags: [splunk, browser-flow, cookies]
---

## create-browser-job

tool: splunk
operation: search
authMode: cookie
cookie: ${SPLUNK_AUTH_COOKIE}

# Path from browser (using raw servicesNS)

path: "en-gb/splunkd/__raw/servicesNS/${SPLUNK_USERNAME}/SHC_tesco_app_digitalcontent/search/v2/jobs"
headers:
  x-requested-with: "XMLHttpRequest"
  x-splunk-form-key: "${SPLUNK_FORM_KEY}"
search: |
search prod_eun_app_logs(dcxp-content-delivery-api)
| transaction source maxspan=300s
params:
auto_cancel: 625
status_buckets: 300
output_mode: "json"
custom.display.page.search.mode: "fast"
custom.dispatch.sample_ratio: 1
ui_dispatch_app: "SHC_tesco_app_digitalcontent"
preview: true
search_level: "fast"
indexedRealtime: 0
check_risky_command: false
provenance: "UI:Search"
earliest_time: "-15m"
latest_time: "now"

## wait-for-job
tool: agent
operation: extract
target: "SID"
data: "${create-browser-job.body}"

## get-job-summary

tool: splunk
operation: summary
authMode: cookie
cookie: ${splunk-login.cookie}

# Custom summary path

path: "en-gb/splunkd/__raw/servicesNS/${SPLUNK_USERNAME}/SHC_tesco_app_digitalcontent/search/v2/jobs/${wait-for-job.sid}/summary"
headers:
x-requested-with: "XMLHttpRequest"
x-splunk-form-key: "${SPLUNK_FORM_KEY}"
method: "GET"
params:
output_mode: "json"

## get-job-results

tool: splunk
operation: results
authMode: cookie
cookie: ${splunk-login.cookie}

# Custom results path

path: "en-gb/splunkd/__raw/servicesNS/${SPLUNK_USERNAME}/SHC_tesco_app_digitalcontent/search/v2/jobs/${wait-for-job.sid}/results?output_mode=json"
headers:
x-requested-with: "XMLHttpRequest"
x-splunk-form-key: "${SPLUNK_FORM_KEY}"
method: "GET"
params:
output_mode: "json"
count: 100

## final-analysis

tool: agent
operation: think
prompt: |
Analyze the final results from the browser-mimic job:
${get-job-results.body}
