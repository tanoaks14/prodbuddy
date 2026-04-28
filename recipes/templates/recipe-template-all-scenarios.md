---
name: recipe-template-all-scenarios
description: Template recipe covering common scenarios across all tools. Copy this file, keep only the steps you need, and fill vars via --vars or .env.
tags: [template, all-tools, troubleshooting, observability]
---

## system-list-tools
tool: system
operation: list_tools

## system-tool-details
tool: system
operation: tool_details
name: ${SYSTEM_TOOL_NAME}

## system-agent-config
tool: system
operation: agent_config

## system-tool-compatibility
tool: system
operation: tool_compatibility
intent: ${ROUTE_INTENT}

## code-summary
tool: codecontext
operation: summary
projectPath: ${PROJECT_PATH}

## code-search
tool: codecontext
operation: search
projectPath: ${PROJECT_PATH}
query: ${CODE_QUERY}

## code-build-graph
tool: codecontext
operation: build_graph_db
projectPath: ${PROJECT_PATH}
dbPath: ${CODE_DB_PATH}

## code-refresh-graph
tool: codecontext
operation: refresh_graph_db
projectPath: ${PROJECT_PATH}
dbPath: ${CODE_DB_PATH}
forceRefresh: false

## code-context-from-query
tool: codecontext
operation: context_from_query
projectPath: ${PROJECT_PATH}
dbPath: ${CODE_DB_PATH}
query: ${INCIDENT_QUERY}

## code-incident-report
tool: codecontext
operation: incident_report
projectPath: ${PROJECT_PATH}
dbPath: ${CODE_DB_PATH}
query: ${INCIDENT_QUERY}

## code-query-graph
tool: codecontext
operation: query_graph
dbPath: ${CODE_DB_PATH}
sql: ${GRAPH_SQL}

## code-p1-context
tool: codecontext
operation: p1_context
projectPath: ${PROJECT_PATH}
dbPath: ${CODE_DB_PATH}
symptom: ${SYMPTOM}

## code-p1-tool-calls
tool: codecontext
operation: p1_tool_calls
projectPath: ${PROJECT_PATH}
dbPath: ${CODE_DB_PATH}
symptom: ${SYMPTOM}

## http-get
tool: http
operation: get
url: ${HTTP_BASE_URL}/health
authEnabled: true
bearerToken: ${HTTP_BEARER_TOKEN}

## http-post
tool: http
operation: post
url: ${HTTP_BASE_URL}/api/query
contentType: application/json
authEnabled: true
bearerToken: ${HTTP_BEARER_TOKEN}
body: ${HTTP_POST_BODY}

## http-put
tool: http
operation: put
url: ${HTTP_BASE_URL}/api/resource/${RESOURCE_ID}
contentType: application/json
authEnabled: true
bearerToken: ${HTTP_BEARER_TOKEN}
body: ${HTTP_PUT_BODY}

## http-patch
tool: http
operation: patch
url: ${HTTP_BASE_URL}/api/resource/${RESOURCE_ID}
contentType: application/json
authEnabled: true
bearerToken: ${HTTP_BEARER_TOKEN}
body: ${HTTP_PATCH_BODY}

## http-delete
tool: http
operation: delete
url: ${HTTP_BASE_URL}/api/resource/${RESOURCE_ID}
authEnabled: true
bearerToken: ${HTTP_BEARER_TOKEN}

## http-head
tool: http
operation: head
url: ${HTTP_BASE_URL}/health

## newrelic-scenarios
tool: newrelic
operation: scenarios

## newrelic-query-metrics
tool: newrelic
operation: query_metrics
metric: ${NEWRELIC_METRIC}
timeWindowMinutes: 15
limit: 100
groupBy: ${NEWRELIC_GROUP_BY}
filters: ${NEWRELIC_FILTERS_JSON}

## newrelic-validate
tool: newrelic
operation: validate
metric: ${NEWRELIC_METRIC}
timeWindowMinutes: 15
limit: 100
groupBy: ${NEWRELIC_GROUP_BY}
filters: ${NEWRELIC_FILTERS_JSON}

## newrelic-query
tool: newrelic
operation: query
metric: ${NEWRELIC_METRIC}
timeWindowMinutes: 15
limit: 100
groupBy: ${NEWRELIC_GROUP_BY}
filters: ${NEWRELIC_FILTERS_JSON}

## splunk-search
tool: splunk
operation: search
queryString: ${SPLUNK_QUERY}
earliestTime: -30m
latestTime: now
count: 50

## splunk-oneshot
tool: splunk
operation: oneshot
search: ${SPLUNK_SEARCH_PIPELINE}
count: 50

## splunk-jobs
tool: splunk
operation: jobs
count: 20

## splunk-results
tool: splunk
operation: results
sid: ${SPLUNK_SID}
count: 50

## elastic-analyze
tool: elasticsearch
operation: analyze
field: message
value: ${SYMPTOM}
size: 50

## elastic-query
tool: elasticsearch
operation: query
index: ${ELASTIC_INDEX}
queryString: ${ELASTIC_QUERY}
size: 50

## elastic-count
tool: elasticsearch
operation: count
index: ${ELASTIC_INDEX}
queryString: ${ELASTIC_QUERY}

## elastic-request
tool: elasticsearch
operation: request
index: ${ELASTIC_INDEX}
endpoint: _search
method: POST
queryDsl: ${ELASTIC_QUERY_DSL_JSON}

## kubectl-get
tool: kubectl
operation: get
resource: pods
namespace: ${K8S_NAMESPACE}
args: --selector=app=${K8S_APP_LABEL}
execute: false

## kubectl-describe
tool: kubectl
operation: describe
resource: pod
name: ${K8S_POD_NAME}
namespace: ${K8S_NAMESPACE}
execute: false

## kubectl-logs
tool: kubectl
operation: logs
resource: pod
name: ${K8S_POD_NAME}
namespace: ${K8S_NAMESPACE}
args: --tail=200
execute: false

## kubectl-top
tool: kubectl
operation: top
resource: pods
namespace: ${K8S_NAMESPACE}
execute: false

## kubectl-version
tool: kubectl
operation: version
execute: false

## kubectl-cluster-info
tool: kubectl
operation: cluster-info
execute: false

## kubectl-api-resources
tool: kubectl
operation: api-resources
execute: false

## kubectl-raw
tool: kubectl
operation: raw
command: kubectl get pods -n ${K8S_NAMESPACE}
execute: false

## kubectl-command
tool: kubectl
operation: command
resource: get
args: pods -n ${K8S_NAMESPACE}
execute: false

## pdf-read
tool: pdf
operation: read
path: ${PDF_INPUT_PATH}

## pdf-create
tool: pdf
operation: create
path: ${PDF_OUTPUT_PATH}
content: ${PDF_CONTENT}
