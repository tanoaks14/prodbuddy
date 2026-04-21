# ProdBuddy

ProdBuddy is a modular Java application that provides pluggable tools for:
- PDF read/create workflows (via OpenDataLoader PDF integration adapter)
- Elasticsearch analysis and query creation
- New Relic data retrieval
- Splunk data retrieval with least-privilege credentials

The codebase enforces strict design rules:
- SOLID-first design
- Maximum class length of 300 lines
- Small, focused classes and interfaces
- Rules-based routing for V1

## Modules

- `prodbuddy-core`: shared contracts and registry
- `prodbuddy-tools-pdf`: PDF tool integration layer
- `prodbuddy-tools-elasticsearch`: Elasticsearch tool
- `prodbuddy-tools-newrelic`: New Relic tool
- `prodbuddy-tools-splunk`: Splunk search tool (read-only)
- `prodbuddy-tools-http`: Generic API/HTTP tool with optional auth
- `prodbuddy-tools-kubectl`: Kubernetes command preview/execution tool
- `prodbuddy-tools-codecontext`: Java code context and repository search tool
- `prodbuddy-core` system catalog tool: discovery and agent config access via `intent=system`
- `prodbuddy-orchestrator`: router and agent loop
- `prodbuddy-app`: runnable entrypoint

## Build

Requires JDK 25.
Set `JAVA_HOME` to your JDK 25 installation before building.

PowerShell example:

```powershell
$env:JAVA_HOME = "C:\\Program Files\\Java\\jdk-25"
$env:Path = "$env:JAVA_HOME\\bin;$env:Path"
```

```bash
mvn clean verify
```

Copy `.env.example` to `.env` and fill in the values for local development.

## Start With Local LLM

Use the PowerShell helper script to start the app with Ollama defaults:

The script prefers `d:/tools/jdk/jdk-25` when present so it runs with JDK 25 even if your shell is currently pointing at another JDK.

```powershell
./start-local-llm.ps1
```

Pass a prompt to the local LLM:

```powershell
./start-local-llm.ps1 "Summarize available code context operations"
```

The local LLM launcher pins `AGENT_MODEL` to `gemma4:e4b`.

Run a full issue-debug loop across code context and telemetry tools:

```powershell
mvn -q -f prodbuddy-app/pom.xml exec:java -Dexec.args="--debug-issue payment timeout 5xx in checkout"
```

`--debug-issue` executes a multi-step triage loop and prints a consolidated report with:
- system/tool inventory checks
- code incident report (`codecontext`)
- New Relic error metrics query
- Splunk search query
- Elasticsearch log query
- kubectl pod-state preview
- suggested next actions when a step fails

Debug step output now includes:
- the function call used (for example `splunk.oneshot`)
- the key input values used for that step (for example `index`, `terms`)
- clear failure reason with attempted operation/path/auth mode for faster troubleshooting

When required telemetry settings are missing, the CLI prompts for values at runtime (you can press Enter to skip).
For code-based investigations, debug mode now also confirms:
- project path used for `codecontext` (`PRODBUDDY_PROJECT_PATH`)
- graph DB path (`CODE_CONTEXT_DB_PATH`)
- optional code location hint (`PRODBUDDY_CODE_HINT`) to improve subsequent assistant/tool targeting
Debug mode prints explicit input context, then a compact summary by default.

## Recipes

List available recipes:

```powershell
mvn -q -f prodbuddy-app/pom.xml exec:java -Dexec.args="--list-recipes"
```

Run a recipe:

```powershell
mvn -q -f prodbuddy-app/pom.xml exec:java -Dexec.args="--run-recipe payment-timeout-debug --vars SYMPTOM=\"payment timeout 5xx\""
```

Template for broad scenario coverage is available at `recipes/recipe-template-all-scenarios.md`.
Copy it, keep only the steps you need, and set variables through `--vars` or `.env`.

## Automated Incident Diagnosis Sandbox [New]

A self-contained Dockerized environment for end-to-end testing of the `incident-diagnosis` recipe.

### 1. Setup & Requirements
- **Docker & Docker Compose** installed.
- **New Relic Account**: You need a License Key (for data ingestion) and a User API Key (for queries).
- Follow the [New Relic Setup Guide](docs/new-relic-setup.md) to get your keys.

### 2. Configuration
Create a `.env` file in the `test-env/` directory (use `.env.test.example` as a template) and provide your NR/Splunk credentials.

### 3. Running the Sandbox
The sandbox includes:
- **Splunk**: Local log search service.
- **Ollama**: Local AI reasoning engine.
- **DemoApp**: A Java service that sends data to NR and logs to Splunk.

**One-Click Start and Analysis:**
```powershell
cd test-env
.\run-test.ps1
```

**What happens?**
1. Services start in Docker.
2. `Ollama` pulls the `gemma4:e4b` model.
3. `DemoApp` is called to trigger a 500 error (`/incident`).
4. The system waits 90s for New Relic aggregation.
5. The `incident-diagnosis` recipe is executed automatically.

### 4. Direct Recipe Execution
You can also run the diagnosis manually against the sandbox:
```powershell
mvn -pl prodbuddy-app exec:java "-Dexec.args=--run-recipe incident-diagnosis"
```
Detailed results are saved to `incident-diagnosis-context.md`.


## Environment Variables

- `ELASTICSEARCH_BASE_URL`
- `ELASTICSEARCH_API_KEY` (optional)
- `NEWRELIC_ACCOUNT_ID`
- `NEWRELIC_USER_API_KEY`
- `SPLUNK_BASE_URL`
- `SPLUNK_AUTH_MODE` (`token`, `user`, or `sso`)
- `SPLUNK_TOKEN` (required for `SPLUNK_AUTH_MODE=token`)
- `SPLUNK_USERNAME` and `SPLUNK_PASSWORD` (required for `SPLUNK_AUTH_MODE=user`)
- `SPLUNK_SESSION_KEY` (required for `SPLUNK_AUTH_MODE=sso`)
- `SPLUNK_DEFAULT_INDEX` (used by debug-loop Splunk searches when no index is provided)

Agent configuration is env-driven and supports Ollama for the first iteration.
- `AGENT_ENABLED`
- `AGENT_PROVIDER`
- `AGENT_BASE_URL`
- `AGENT_MODEL`
- `AGENT_CHAT_PATH`
- `AGENT_AUTH_ENABLED`
- `AGENT_API_KEY`
- `AGENT_STREAM_ENABLED` (enables streaming response mode from local model API)
- `AGENT_THINKING_ENABLED` (passes thinking flag to local model when supported)
- `AGENT_FUNCTION_CALLING_ENABLED` (prompt-level function-call output mode)
- `AGENT_FUNCTION_CALLING_WITH_THINKING` (allows function-call mode while thinking is enabled)
- `PRODBUDDY_PROJECT_PATH` (used by debug codecontext calls)
- `PRODBUDDY_CODE_HINT` (optional module/path/class hint for code-based requests)

Function-calling note: current local integration uses `/api/generate`, so function calling is prompt-driven JSON output, not native provider tool-calling protocol.

Additional env-backed properties are available in `.env.example`, including auth flags, defaults, generic HTTP settings, and kubectl settings.

## New Relic Wrapper

`newrelic` now supports structured operations for agent-driven query generation:
- `scenarios`
- `query_metrics`
- `query` (compatibility)
- `validate`

Example payload for `query_metrics`:

```json
{
	"metric": "latency",
	"filters": {"appName": "checkout"},
	"timeWindowMinutes": 10,
	"limit": 100,
	"groupBy": ""
}
```

## Elasticsearch Tool

`intent=elasticsearch` operations:
- `analyze`
- `query` / `search`
- `count`
- `request` (custom endpoint/method/body)

Read-only enforcement:
- Only read endpoints/methods are allowed (`_search`, `_count`, `_msearch`, `_field_caps`, `_mapping`, `_settings`, `_cat/*`).
- Write/destructive endpoints are blocked.

Examples:

```json
{ "operation": "search", "payload": { "index": "logs-*", "queryString": "service:checkout AND error", "size": 50 } }
```

```json
{ "operation": "request", "payload": { "index": "logs-*", "endpoint": "_search", "method": "POST", "queryDsl": { "query": { "match_all": {} } } } }
```

## Splunk Tool

`intent=splunk` operations:
- `search`
- `oneshot`
- `jobs`
- `results` (requires `sid`)

Authentication modes:
- `SPLUNK_AUTH_MODE=token`: uses `SPLUNK_TOKEN`
- `SPLUNK_AUTH_MODE=user`: logs in with `SPLUNK_USERNAME` and `SPLUNK_PASSWORD` and uses a Splunk session key
- `SPLUNK_AUTH_MODE=sso`: uses a pre-issued `SPLUNK_SESSION_KEY` (for SSO-only environments)

Richer query input is supported through payload fields such as:
- `search` / `query` / `queryString`
- `index`, `host`, `source`, `sourcetype`, `terms`
- `earliestTime`, `latestTime`, `count`, `outputMode`

Examples:

```json
{ "operation": "search", "payload": { "queryString": "service=checkout error", "earliestTime": "-30m", "latestTime": "now", "count": 100 } }
```

```json
{ "operation": "search", "payload": { "index": "main", "source": "checkout", "terms": "timeout OR 5xx", "count": 50 } }
```

```json
{ "operation": "results", "payload": { "sid": "1712573291.123", "count": 100 } }
```

## Kubectl Tool

`intent=kubectl` (or `intent=kubernetes`) supports any kubectl command pattern via structured args or raw command.

Read-only enforcement:
- Allowed command families: `get`, `describe`, `logs`, `top`, `version`, `cluster-info`, `api-resources`, `raw`, `command`.
- Destructive verbs like `delete`, `apply`, `patch`, `scale`, `exec`, `port-forward` are blocked.

Examples:

```json
{ "operation": "get", "payload": { "resource": "pods", "args": ["-A"], "flags": { "selector": "app=checkout", "watch": true }, "execute": false } }
```

```json
{ "operation": "raw", "payload": { "command": "kubectl get pods --selector \"app=checkout api\"", "execute": false } }
```

## System Catalog Tool

`intent=system` operations:
- `list_tools`
- `tool_details` with payload `{ "name": "newrelic" }`
- `agent_config`
- `tool_compatibility` with payload `{ "intent": "new relic" }`

## Java Code Context Tool

`intent=codecontext` operations:
- `summary` with payload `{ "projectPath": "D:/apps/my-java-service" }`
- `search` with payload `{ "projectPath": "D:/apps/my-java-service", "query": "NullPointerException" }`
- `p1_context` with payload `{ "projectPath": "D:/apps/my-java-service", "symptom": "timeout" }`
- `build_graph_db` with payload `{ "projectPath": "D:/apps/my-java-service", "dbPath": ".prodbuddy/codegraph" }`
- `refresh_graph_db` with payload `{ "projectPath": "D:/apps/my-java-service", "dbPath": ".prodbuddy/codegraph", "forceRefresh": false }`
- `context_from_query` with payload `{ "projectPath": "D:/apps/my-java-service", "dbPath": ".prodbuddy/codegraph", "query": "payment timeout in checkout" }`
- `incident_report` with payload `{ "projectPath": "D:/apps/my-java-service", "dbPath": ".prodbuddy/codegraph", "query": "payment timeout 5xx in checkout" }`
- `query_graph` with payload `{ "dbPath": ".prodbuddy/codegraph", "sql": "SELECT * FROM ClassNode LIMIT 20" }`
- `p1_tool_calls` with payload `{ "projectPath": "D:/apps/my-java-service", "symptom": "timeout" }`

`context_from_query` returns a richer bundle for natural-language requests:
- intent category and extracted entities
- primary code matches
- graph summary (if local graph DB is available)
- ranked findings with simple explainability signals
- suggested next operations

`incident_report` returns a deterministic cross-system report:
- code context bundle
- telemetry query plan (New Relic, Splunk, Elasticsearch)
- fixed correlation strategy and rules
- recommended execution order for incident triage

For P1 investigations, `p1_context` returns:
- Project summary
- Relevant code matches
- Recommended follow-up queries for New Relic, Splunk, and Elasticsearch

Inspired by java2graph, the tool now supports a local graph DB pipeline:
1. extract Java structural graph (ClassNode, MethodNode, Defines)
2. persist to local embedded DB
3. query graph with read-only SQL for fast incident context

`refresh_graph_db` supports on-demand rebuilds and change-aware updates:
- `forceRefresh=true` always rebuilds from scratch
- `forceRefresh=false` rebuilds only when Java source fingerprint changes

Set `CODE_CONTEXT_MAX_RESULTS` in `.env` to control search output size.
Set `CODE_CONTEXT_DB_PATH` in `.env` to control local graph DB location.
Set `ELASTICSEARCH_TIMEOUT_SECONDS` and `ELASTICSEARCH_MAX_BODY_CHARS` for Elasticsearch operational hardening.
Set `KUBECTL_TIMEOUT_SECONDS` and `KUBECTL_MAX_OUTPUT_CHARS` for kubectl operational hardening.

## Notes on OpenDataLoader PDF

The PDF module is designed to integrate with `opendataloader-pdf` through an adapter boundary so implementation details can evolve without changing tool contracts.
