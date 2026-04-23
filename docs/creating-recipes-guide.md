# ProdBuddy Recipe Generation Guide (Comprehensive)

A **Recipe** is a sequence of diagnostic steps executed by the ProdBuddy Orchestrator. Recipes are stored in the `recipes` directory as Markdown files with YAML-like metadata and structured steps.

---

## 1. Global Syntax Rules

### **Structure**
- **Metadata**: Top of the file between `---` blocks (name, description, tags).
- **Steps**: Denoted by `## step-name` headings (use dashes, no spaces).
- **Key Order**: `tool:` and `operation:` MUST be the first two keys in a step.
- **Multi-line**: Use the `|` character for multi-line queries (GQL, NRQL, SPL).
- **Truncation Control**: Most tools truncate output at 20,000 characters by default. Use `noTruncate: true` to disable this or `maxOutputChars: <number>` to customize it.
- **Logging Control**: Use `logFullResponse: true` to print the complete response body in the CLI/Logs instead of a summary.

### **Variable Interpolation (`${...}`)**
- **Environment**: `${VAR_NAME}` looks up values in `.env` or system environment.
- **Step Results**: `${step-name.field}` or `${step-name.result.path}`.
- **Loops**: `${item}` (default) or `${customAsName}`.
- **Smart Body Unwrapping**: 
    - `${step.body}`: Raw response string.
    - `${step.jsonBody}`: Auto-parsed JSON object (for HTTP tool).
    - `${step.data}`: Root data object (for GraphQL/Elastic/Splunk).

---

## 2. Tool Reference

### **GraphQL (`graphql`)**
- **Operations**: `query`, `introspect`, `list_operations`.
- **Authentication (`auth` block)**:
    - `mode`: `none`, `bearer`, `basic`, `cookie`, `header`.
    - `token`, `username`, `password`, `headerName`.
- **Parameters**: `url`, `query`, `variables` (Map).

### **Splunk (`splunk`)**
- **Operations**: `oneshot`, `search`, `results`, `login`, `jobs`.
- **Auth Modes**: `token`, `user`, `sso`, `session`, `cookie`.
- **Keys**: `search`, `query`, `earliestTime`, `latestTime`, `count`, `execMode`, `sid`.
- **Compose Keys**: `index`, `host`, `source`, `sourcetype`, `terms`.

### **New Relic (`newrelic`)**
- **Operations**: `query`, `query_metrics`, `list_apps`, `list_external_services`, `get_trace`, `list_dashboards`, `get_dashboard`, `gql_query`.
- **Keys**: `metric`, `metric_name`, `scenario`, `time_window` (e.g. "last 1 hour"), `limit`, `filters` (Map), `groupBy`, `guid`, `traceId`, `graphqlBody`.

### **Elasticsearch (`elasticsearch`)**
- **Operations**: `search`, `count`, `request`, `query`.
- **Keys**: `index`, `body`, `endpoint` (for raw requests).

### **Kubectl (`kubectl`)**
- **Operations**: `get`, `describe`, `logs`, `apply`, `raw`, `command`.
- **Keys**: `resource`, `name`, `namespace`, `container`, `execute` (boolean), `args` (List), `flags` (Map), `binary`.

### **CodeContext (`codecontext`)**
- **Operations**: `summary`, `search`, `call_chain`, `complexity_report`, `change_impact`, `dead_code`, `query_graph`, `p1_context`, `incident_report`.
- **Keys**: `startMethodId`, `direction` (UP/DOWN), `maxDepth`, `query`, `sql`, `className`, `topN`, `symptom`.

### **JSON (`json`)**
- **Operations**: `extract`, `assert`, `search`.
- **Keys**: `data`, `path`, `expected`, `paths` (Map), `regex` (Map), `query`.

### **HTTP (`http`)**
- **Operations**: `get`, `post`, `put`, `patch`, `delete`, `head`.
- **Keys**: `url`, `method`, `body`, `contentType`, `authEnabled`, `bearerToken`.

### **Git (`git`)**
- **Operations**: `diff`, `status`, `log`.
- **Keys**: `base`, `count`.

### **Agent (`agent`)**
- **Operations**: `think`, `generate_recipe`, `validate_recipe`.
- **Keys**: `prompt`, `objective`, `recipe` (content).

### **System (`system`)**
- **Operations**: `list_tools`, `tool_details`, `agent_config`, `ask`.
- **Key**: `question` (for `ask` operation).

---

## 3. Control Flow & Logic

### **Conditions (`condition`)**
- **Operators**: `==`, `!=`, `contains`, `>`, `<`, `>=`, `<=`.
- **Logic**: `&&`, `||`, `!`.
- **Numeric**: Automatically handles numbers if both sides are valid.

### **Loops (`foreach`)**
- **foreach**: Reference to a list or comma-sep string.
- **as**: Name of loop variable (default: `item`).
- **stopOnFailure**: Abort loop if a sub-step fails.
- **steps**: List of nested steps starting with `- name:`.

---

### **Master Example (Agent Template)**
```markdown
---
name: master-diagnostic-template
description: Complex multi-tool chain with loops and logic
tags: [template, multi-tool]
---

## check-splunk
tool: splunk
operation: oneshot
search: |
  index=main status=500
  | stats count by serviceName

## analyze-critical
tool: agent
operation: think
condition: ${check-splunk.data.count} > 0
prompt: |
  Determine if we need to deep dive into: ${check-splunk.data.results}

## process-services
foreach: ${check-splunk.data.results}
as: svc
stopOnFailure: true
condition: ${analyze-critical.opinion} contains 'DEEP_DIVE'
steps:
  - name: get-code-context
    tool: codecontext
    operation: search
    query: ${svc.serviceName}
  - name: verify-k8s
    tool: kubectl
    operation: get
    resource: pods
    flags:
      selector: "app=${svc.serviceName}"
```
