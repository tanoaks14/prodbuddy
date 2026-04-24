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
- **Operations**:
    - `query`: Run custom NRQL queries (returns JSON data).
    - `list_dashboards`: Search for dashboards and get GUIDs.
    - `get_dashboard`: Get widget details and page GUIDs for a dashboard.
    - `snapshot`: Generate a temporary URL for a dashboard page image.
        - Parameters: `guid` (Dashboard Page GUID).
        - Note: You can modify the returned URL's `?format=PDF` to `?format=PNG` to get an image.
    - `get_trace`: Retrieve distributed tracing details by Trace ID.
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
- **Operations**: `extract`, `assert`, `search`, `parse`.
- **Keys**: `data`, `path`, `expected`, `paths` (Map), `regex` (Map), `query`.

### **HTTP / API (`http`)**
- **Operations**: `get`, `post`, `put`, `patch`, `delete`, `download_base64`.
- **Keys**: `url`, `method`, `body`, `headers` (Map), `authEnabled`, `bearerToken`, `noTruncate`, `maxOutputChars`.
- **Note**: `download_base64` returns binary content as a Base64 string in the `base64` field.

### **Agent (`agent`)**
- **Operations**: `think`, `extract`, `wait`, `loop`, `generate_recipe`, `validate_recipe`.
- **Keys**: `prompt`, `image` (Base64 string for multimodal analysis), `objective`, `target`, `data`, `seconds`, `tools` (List).

### **DateTime (`datetime`)**
- **Operations**: `convert`.
- **Keys**: `value`, `from` (iso/epoch), `to` (iso/epoch/pattern), `zone` (UTC/etc).

### **Interactive Patterns**

When building interactive recipes, use the `agent.think` tool to format raw JSON outputs into human-readable options before asking the user for a selection:

```yaml
## list-options
tool: agent
operation: think
prompt: "Format these results as a list for the user: ${previous-step.body}"

## ask-selection
tool: interactive
operation: ask
prompt: "Pick a GUID from the list above:"
```

### **Interactive (`interactive`)**
- **Operations**: `ask`.
- **Keys**: `question`.
- **Note**: Pauses execution to wait for user input.

### **Recipe (`recipe`)** (Lego Mode)
- **Operations**: `run`.
- **Keys**: `name` (of the sub-recipe).
- **Note**: Merges steps from the target recipe into the current run.

### **System (`system`)**
- **Operations**: `list_tools`, `tool_details`, `agent_config`.

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

---

### **4. Lego Recipes (Modular Composition)**

You can reuse existing recipes as building blocks. When a recipe is run as a step, its steps are executed in the current context, and its results are added to the shared variable pool.

```markdown
## 1. health-check
tool: recipe
operation: run
name: common-health-check

## 2. specialized-audit
tool: agent
operation: loop
condition: ${health-check.steps_executed} > 0
prompt: |
  Since health check passed, perform a deep audit of the last 3 commits.
```
```
