---
name: local-docker-test
description: Executes commands against the locally hosted Docker stack (Splunk and Elasticsearch) to prove API connectivity and query resolution natively. Note that New Relic has no localized Docker backend, so it's ignored.
tags: [test, local, docker, elastic, splunk]
---

## step-1-elastic-health
tool: elasticsearch
operation: request
endpoint: /
method: GET
authEnabled: false

## step-2-elastic-count
tool: elasticsearch
operation: count
index: _all
authEnabled: false

## step-3-splunk-search
tool: splunk
operation: search
search: index=_internal sourcetype=splunkd | head 5
authEnabled: true
authMode: user
# Credentials are map-matched to your .env or the Docker build args
