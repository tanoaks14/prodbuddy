# ProdBuddy Recipe Generation Guide

In ProdBuddy, a **Recipe** is an automated playbook of sequential execution steps. Think of it like a macro or a shell script specifically engineered for deep-diving incident analysis using the Agent toolset.

## Basic Structure
All recipes must be in YAML-like Markdown files stored inside the `recipes` directory. They leverage standard Markdown headings (`## step-name`) to segregate discrete pipeline segments.

```markdown
---
name: sample-diagnostic
description: A quick template
tags: [sample, api]
---

## first-step
tool: http
operation: get
url: https://api.sample.com
```

## Supported Tools (Full Reference)

Each `tool` corresponds to an internal service in the ProdBuddy Orchestrator. Below is a comprehensive list of all supported tools and their operations.

### 1. Observability & Logs

#### **Splunk** (`splunk`)
Read-only search tool for Splunk indices.
- **Operations:**
    - `oneshot`: Executes a search synchronously (preferred for small datasets).
    - `search`: Starts an asynchronous search job.
    - `results`: Retrieves results from an async job.
    - **`login` (New):** Performs explicit authentication and returns the raw `sessionKey` and formatted `cookie`.
- **Key Parameters:**
    - `search` / `query` (string): The SPL query.
    - `authMode` (string): `token` (default), `user`, `sso`, `cookie`.
    - `cookie` (string): Manual session override (bypasses background login).
- **Advanced Usage:**
    - **Generating Commands**: If your query starts with `|` (e.g. `| makeresults`), do NOT include the `search` keyword. The orchestrator will handle this automatically.
    - **Port Detection**: When using `authMode: cookie`, the tool automatically extracts the management port from `SPLUNK_BASE_URL` to format the cookie header correctly.
- **Example:**
```markdown
## find-errors
tool: splunk
operation: oneshot
search: index=main error | head 5
```

#### **Elasticsearch** (`elasticsearch`)
Introspective query tool for Elastic clusters.
- **Operations:**
    - `query` / `search`: Executes a JSON query against `_search`.
    - `count`: Returns document count for a query.
    - `analyze`: Syntax validation for the query.
    - `request` (New): Raw endpoint execution (e.g. `/_cat/indices`).
- **Parameters:**
    - `index` (string): Target index (defaults to `_all`).
    - `endpoint` (string): Path for `request` op.
- **Example:**
```markdown
## check-logs
tool: elasticsearch
operation: search
index: logs-prod
body: { "query": { "match_all": {} } }
```

#### **New Relic** (`newrelic`)
NRQL execution and metric analysis.
- **Operations:**
    - `query_metrics`: Targeted metric retrieval.
    - `scenarios`: Lists available query templates.
    - `list_dashboards`: Search for dashboard GUIDs.
    - `get_dashboard`: Detailed widget and page analysis.
    - `list_apps`: List APM service entities.
    - `list_external_services`: Correlate upstream dependencies.
    - `get_trace`: Extraction of distributed trace spans.
    - **Parameters:**
    - `metric` (string): Target metric name.
    - `guid` (string): Dashboard/Application GUID.
    - `traceId` (string): ID for distributed trace.
- **Example:**
```markdown
## check-throughput
tool: newrelic
operation: query_metrics
metric: apm.service.transaction.duration
timeWindowMinutes: 15
```

### 2. Infrastructure & VCS

#### **Kubectl** (`kubectl`)
Kubernetes introspection and management.
- **Operations:**
    - `get`, `describe`, `logs`: Standard Read-only operations.
    - `apply`: Update resources (requires `KUBECTL_EXECUTE=true`).
- **Parameters:**
    - `resource` (string): e.g., `pods`, `deployments`.
    - `name` (string): Name of the specific resource.
- **Example:**
```markdown
## get-pod-status
tool: kubectl
operation: get
resource: pods
```

#### **Git** (`git`)
Introspects the local repository state.
- **Operations:**
    - `diff`: Lists changed files.
    - `status`: Short summary of local index.
    - `log`: Git commit history.
- **Parameters:**
    - `base` (string): Base ref for diff (e.g., `HEAD~1`).
- **Example:**
```markdown
## find-recent-changes
tool: git
operation: diff
base: origin/main
```

### 3. Code Analysis

#### **CodeContext** (`codecontext`)
Powerful static analysis and graph-based introspection for Java projects.
- **Operations:**
    - `summary`: High-level project structure summary.
    - `search`: Keyword-based code search across the project.
    - `call_chain`: **(New)** Bidirectional call graph traversal with exact source code extraction.
    - `complexity_report`: Class-level cyclomatic complexity heatmaps.
    - `change_impact`: Predict the blast radius of a class change.
    - `dead_code`: Discover unreachable code segments.
    - `query_graph`: Direct SQL access to the H2 graph database.
- **Parameters:**
    - `startMethodId` (string): FQN of the method to start traversal (for `call_chain`).
    - `direction` (string): `UP` (callers) or `DOWN` (callees).
    - `maxDepth` (int): Depth of recursion (default: 3).
- **Example:**
```markdown
## trace-upstream-dependencies
tool: codecontext
operation: call_chain
startMethodId: com.prodbuddy.core.tool.Tool#execute(ToolRequest,ToolContext)
direction: UP
maxDepth: 2
```

### 4. Utilities

#### **HTTP** (`http`)
Generic API interaction tool.
- **Operations:** `get`, `post`, `put`, `patch`, `delete`, `head`.
- **Key Parameters:**
    - `url` (string): Target API endpoint.
    - `authEnabled` (boolean): Whether to include tokens (default depends on config).
    - `body` (string): Request payload for POST/PUT.

#### **JSON** (`json`)
Validation and path extraction for JSON data.
- **Operations:**
    - `assert`: Fail the recipe if a value at a path doesn't match.
    - `search`: Find all paths for a specific key.
    - `extract`: **(New)** Multi-strategy data extraction using JSON Path and Regex.
- **Key Parameters:**
    - `data` (string): The raw JSON or text string.
    - `paths` (Map): Field names to JSON Path (dot-notation).
    - `regex` (Map): Field names to Regex patterns (use capture groups for specific values).
- **Example:**
```markdown
## process-nr-data
tool: json
operation: extract
data: ${step-fetch.body}
paths:
  account_id: data.actor.account.id
regex:
  error_trend: "\"count\":\\s*(\\d+)"
```

#### **PDF** (`pdf`)
Document processing utility.
- **Operations:** `read` (extract text), `create` (generate document).

#### **System** (`system`)
Discovery and configuration introspection.
- **Operations:** `list_tools`, `tool_details`, `agent_config`.

## Dynamic Result Interpolation

The true power of ProdBuddy Recipes comes from its **resolution engine**, which enables the result of one step to dynamically populate fields down the pipeline.

### Environmental Variables
Fields wrapped directly inside `${}` are extracted from the environmental baseline config context.
Example: `url: ${PAYMENT_SERVICE_URL}` 

### Response Mapping
If you prefix the variable with the *name of a previous step*, the engine will drill into the JSON output of that step and extract the variable directly.
**Syntax:** `${stepName.field}` or `${stepName.result.key}`

**Key Findings:**
- Dash and dot notation (`step-1.field`) is fully supported.
- The `result.` prefix is often required if the tool returns a nested object.
- Example: `${get-auth.cookie}` correctly extracts the formatted session cookie from a `splunk.login` step.

**Example workflow:**

```markdown
## get-random-user
tool: http
operation: get
url: https://jsonplaceholder.typicode.com/users/1
authEnabled: false

## check-user-todos
tool: http
operation: get
# Uses the 'id' returned by get-random-user!
url: https://jsonplaceholder.typicode.com/users/${get-random-user.body.id}/todos
authEnabled: false
```

### Parameters
Each step can include a `condition` and custom parameters. Use `${stepName.field}` to reference previous step results.

```markdown
## step-conditional
tool: agent
operation: think
condition: ${step-1.success} == true
prompt: Analyze results.
```

### Logical Conditions (DSL)
ProdBuddy supports a complex logic DSL in the `condition` field:
- **Comparisons**: `==`, `!=`, `contains`
- **Logic**: `&&` (AND), `||` (OR), `!` (NOT)
- **Nested**: `( ... )` groupings.

Example:
`condition: ${step-1.success} && (${step-decide.opinion} contains 'PROCEED')`

---

## Interactive Recipes
Use the `system.ask` operation to pause execution and request user input. The result is stored in `${stepName.answer}`.

```markdown
## step-wait-for-user
tool: system
operation: ask
question: "Which account ID should we analyze?"
```

---

## PDF Reporting with Charts
The `pdf.create` tool supports basic text rendering and automatic bar chart generation.

```markdown
## step-create-report
tool: pdf
operation: create
path: report.pdf
content: |
  Analysis complete.
  Metrics over time:
  chart_data: 10, 50, 20, 80, 40
```

## Complex Payloads (JSON Assertions)

Sometimes, you do not just want to make successive hits, you want the recipe to intelligently fail early if the infrastructure provides back misaligned data. You can leverage the `json` tool heavily here.

```markdown
## assert-user-id
tool: json
operation: assert
data: ${get-random-user.body}
path: id
expected: 1
```

If the ID is wrong, the Orchestrator flags a failure early and exits the chain cleanly, leaving accurate traces in your sequence viewer.

## AI Generator Reference

> [!NOTE]
> This section is for high-density consumption by the `agent.generate_recipe` operation.

### GLOBAL SYNTAX RULES
- **Heading**: `## step-name` (No spaces, use dashes).
- **Mandatory Keys**: `tool: <name>` and `operation: <op>` MUST be first in step.
- **Interpolation**: `${stepName.field}` ONLY. Do NOT use `{{}}`.
- **Splunk Generating Commands**: For `| makeresults`, do NOT prepend `search`.
- **Port Extraction**: Auto-detected from `SPLUNK_BASE_URL` when in `mode: cookie`.

### CORE OPERATION MAP
- **splunk**: `login`, `oneshot`, `search`.
- **newrelic**: `list_apps`, `get_trace`, `list_dashboards`, `query_metrics`.
- **elasticsearch**: `search`, `request`, `count`.
- **codecontext**: `call_chain`, `summary`, `search`.
- **json**: `extract`, `assert`, `search`.
- **agent**: `think`, `generate_recipe`.
