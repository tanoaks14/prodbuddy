## step-1-splunk
tool: splunk
operation: oneshot
search: "| stats count from index=payment where status=500 earliest=-24h"

## step-2-newrelic
tool: newrelic
operation: query
metric: average_response_time
filters:
  service: payment-api
  guid: ${step-1-splunk.body.someGuid}

## step-3-elastic
tool: elasticsearch
operation: search
query: "transaction_id: ${step-2-newrelic.results[0].id}"
index: order-logs

## step-4-code
tool: codecontext
operation: incident_report
query: "performance degradation in payment flow with GUID ${step-2-newrelic.results[0].id}"

## step-5-health
tool: http
operation: get
url: "https://gateway.internal/health"

## step-6-validate
tool: agent
operation: validate_recipe
recipe: "## self-check\ntool: agent\noperation: think\nprompt: success"
