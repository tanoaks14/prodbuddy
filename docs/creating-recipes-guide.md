# ProdBuddy Recipe Generation Guide (Comprehensive)

A **Recipe** is a sequence of diagnostic steps executed by the ProdBuddy Orchestrator. Recipes are stored in the `recipes` directory as Markdown files with YAML-like metadata and structured steps.

---

## 1. Global Syntax Rules

### **Metadata (Frontmatter)**
- **name**: Descriptive name of the recipe.
- **description**: What the recipe does.
- **tags**: List of tags for categorization (e.g., `[network, debug]`).
- **analysis**: Set to `true` to enable automated AI analysis of the entire recipe run once complete.

### **Structure**
- **Steps**: Denoted by `## step-name` headings (use dashes, no spaces).
- **Key Order**: `tool:` and `operation:` MUST be the first two keys in a step for optimal parsing.
- **Multi-line**: Use the `|` character for multi-line queries (GQL, NRQL, SPL).
- **File References**: Use `@file:path/to/file.ext` to load parameter values (like long queries) from external files. Path is relative to the recipe file.
- **Truncation Control**: Most tools truncate output at 20,000 characters by default. Use `noTruncate: true` to disable this or `maxOutputChars: <number>` to customize it.
- **Logging Control**: Use `logFullResponse: true` to print the complete response body in the CLI/Logs instead of a summary.

### **Variable Interpolation (`${...}`)**
- **Environment**: `${VAR_NAME}` looks up values in `.env` or system environment.
- **Step Results**: `${step-name.field}` or `${step-name.result.path}`.
- **Indexing**: Access list items via `${step.results[0].id}`.
- **Special Fields**:
    - `${step.summary}`: A human-readable summary of the step result.
    - `${step.success}`: Returns "true" if the step succeeded, "false" otherwise.
    - `${step.trend}`: Extracted trend (e.g., `UP`, `DOWN`, `STABLE`) from numeric data.
- **Smart Body Unwrapping**: 
    - `${step.body}`: Raw response string.
    - `${step.jsonBody}`: Auto-parsed JSON object (for HTTP tool).
    - `${step.data}`: Root data object (for GraphQL/Elastic/Splunk).
    - *Note: If a key is not found in the root result, the engine automatically checks inside `result.body` if it's a JSON object.*

---

## 2. Tool Reference

### **GraphQL (`graphql`)**
- **Operations**: `query`, `introspect`, `list_operations`, `format`.
- **Authentication (`auth` block)**:
    - `mode`: `none`, `bearer`, `basic`, `cookie`, `header`.
    - `token`, `username`, `password`, `headerName`.
- **Parameters**: `url`, `query`, `variables` (Map), `validate` (boolean - checks for unresolved placeholders).

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
        - Parameters: `traceId`.
- **Keys**: `metric`, `metric_name`, `scenario`, `time_window` (e.g. "last 1 hour"), `limit`, `filters` (Map), `groupBy`, `guid`, `traceId`, `graphqlBody` (for raw NerdGraph queries).

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
- **Operations**: 
    - `think`: General reasoning. Parameters: `prompt`, `image`/`images` (Base64/Path).
    - `extract`: Extract specific data. Parameters: `target` (e.g., "SID"), `data`.
    - `wait`: Pause execution. Parameters: `seconds`.
    - `loop`: Autonomous goal seeking. Parameters: `prompt`, `tools` (List).
    - `generate_recipe`: Create a new recipe. Parameters: `objective`.
    - `validate_recipe`: Check recipe syntax/logic. Parameters: `recipe` (content).
- **Keys**: `prompt`, `image`, `images`, `objective`, `target`, `data`, `seconds`, `tools`.

### **DateTime (`datetime`)**
- **Operations**: `convert`.
- **Keys**: `value`, `from` (iso/epoch), `to` (iso/epoch/pattern), `zone` (UTC/etc).

### **Git (`git`)**
- **Operations**: `diff`, `status`, `log`.
- **Keys**: `repoPath` (default: "."), `base` (for diff, default: "HEAD~1"), `n` (for log, default: 10).

### **Observation (`observation`)**
- **Operations**: `mermaid` (get sequence diagram), `render` (save as PNG/PDF), `clear`.
- **Keys**: `format` (for render, default: "png").

### **PDF (`pdf`)**
- **Operations**: `read`, `create`.
- **Keys**: `path`, `content` (for create).

### **Interactive Patterns**

When building interactive recipes, use the `agent.think` tool to format raw JSON outputs into human-readable options, and then **embed that output directly into the next interactive prompt**:

```yaml
## list-options
tool: agent
operation: think
prompt: "Format these results as a list for the user: ${previous-step.body}"

## ask-selection
tool: interactive
operation: ask
prompt: |
  Available Options:
  ${list-options.opinion}
  
  Please pick a GUID from the list above:
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
- **Keys**: `toolName` (for tool_details).

---

## 3. Control Flow & Logic

### **Conditions (`condition`)**
- **Operators**: `==`, `!=`, `contains`, `>`, `<`, `>=`, `<=`.
- **Logic**: `&&`, `||`, `!`.
- **Numeric**: Automatically handles numbers if both sides are valid.
- **Placement**: A `condition` can be added to **any** step to control its execution based on prior results.

### **Loops (`foreach`)**
- **foreach**: Reference to a list or comma-sep string.
- **as**: Name of loop variable (default: `item`).
- **stopOnFailure**: Abort loop if a sub-step fails.
- **steps**: List of nested steps starting with `- name:`.

### **Sub-Recipes / Inclusions (`recipe`)**
- **Operations**:
    - `run`: Executes the target recipe as a step. Results are merged into the current run.
    - `include`: Static inclusion. Merges steps from the target recipe into the current recipe at load time.
- **Keys**: `name` (for run), `path` (for include).
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

## 5. AI Best Practices for Recipe Generation

When generating recipes as an AI agent, follow these rules for maximum reliability:

1.  **Descriptive Naming**: Use unique, hyphenated names for steps (e.g., `check-payment-health`) so they are easy to reference in variables.
2.  **Reasoning Links**: Use `agent.think` between complex diagnostic tools to summarize data before passing it to the next step.
3.  **Smart Variables**: Prefer `${step.summary}` for natural language prompts and `${step.data.path}` for structured tool inputs.
4.  **Error Handling**: Use `condition` checks to skip expensive operations (like full snapshots) if previous steps indicate they aren't needed.
5.  **Externalize Queries**: For complex GraphQL or SQL, use `@file:queries/my-query.gql` to keep the recipe file readable.
6.  **Looping**: When processing lists, always use `stopOnFailure: true` unless you explicitly want to continue on partial errors.
```
