---
name: payment-timeout-debug
description: Debug 5xx timeouts in the payment checkout service
tags: [payment, 5xx, timeout, checkout]
---

## check-payment-health
tool: http
operation: get
url: ${PAYMENT_SERVICE_URL}/health
authEnabled: true
bearerToken: ${PAYMENT_API_TOKEN}

## elastic-recent-errors
tool: elasticsearch
operation: query
index: logs-*
queryString: ${SYMPTOM}
size: 50

## elastic-traces
tool: elasticsearch
operation: query
index: traces-*
queryString: ${SYMPTOM}
size: 20

## fetch-trace-detail
tool: http
operation: get
url: ${TRACING_URL}/api/traces/${elastic-traces.hits[0]._id}
authEnabled: true
bearerToken: ${TRACING_TOKEN}

## splunk-error-logs
tool: splunk
operation: oneshot
index: production
terms: ${SYMPTOM}
earliest_time: -30m
count: 50

## check-live-pods
tool: kubectl
operation: get
resource: pods
namespace: ${KUBECTL_NAMESPACE}
args: --selector=app=payment
execute: false
