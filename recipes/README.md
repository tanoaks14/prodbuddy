# ProdBuddy Recipe Catalog

## Structure

```
recipes/
├── primitives/    ← Atomic, single-tool operations (LEGO bricks)
├── workflows/     ← Domain-specific multi-tool chains
├── tests/         ← Per-tool smoke tests & integration tests
├── templates/     ← Reference docs & generation aids
└── demos/         ← Showcases & tutorials
```

## Primitives

Atomic recipes that wrap a single tool operation. Use these as building blocks.

| Tool | Recipe | Description |
|------|--------|-------------|
| **Code** | `code/deep-dive.md` | Multi-step code investigation |
| **Code** | `code/call-chain.md` | Recursive call chain analysis |
| **Code** | `code/complexity.md` | Cyclomatic complexity heatmap |
| **Code** | `code/dead-code.md` | Dead code detection |
| **Code** | `code/change-impact.md` | Change impact analysis |
| **Code** | `code/broken-check.md` | Broken build check |
| **New Relic** | `newrelic/list-apps.md` | List monitored applications |
| **New Relic** | `newrelic/list-dashboards.md` | List dashboards |
| **New Relic** | `newrelic/dashboard-snapshot.md` | Full dashboard data extraction |
| **New Relic** | `newrelic/trace-lookup.md` | Distributed trace lookup |
| **New Relic** | `newrelic/interactive-report.md` | Interactive NR report with PDF |
| **New Relic** | `newrelic/setup.md` | NR connection setup |
| **Splunk** | `splunk/setup.md` | Splunk connection setup |
| **GraphQL** | `graphql/setup.md` | GraphQL endpoint setup |
| **Git** | `git/precheck.md` | Pre-commit checks |
| **Git** | `git/sub-recipe.md` | Git status sub-recipe |

## Workflows

Domain-specific chains that compose primitives to solve real problems.

| Recipe | Description | Tools Used |
|--------|-------------|------------|
| `incident-diagnosis.md` | Full incident RCA: NR → Splunk → Code | newrelic, splunk, codecontext, agent |
| `auto-investigate-errors.md` | 500 errors → log extract → code deep dive loop | splunk, agent, recipe |
| `master-diagnostic.md` | Full-stack diagnostic across all tools | splunk, newrelic, elasticsearch, codecontext, http, agent |
| `payment-timeout-debug.md` | Payment timeout investigation | http, elasticsearch, splunk, kubectl |
| `dashboard-diagnosis.md` | NR dashboard deep dive | newrelic, agent |
| `log-pattern-synthesis.md` | Log pattern extraction and analysis | splunk, agent |
| `autonomous-audit.md` | Self-driven code audit | agent, codecontext |
| `nr-historical-comparison.md` | NR historical data comparison | newrelic, agent |
| `wow-dashboard-comparison.md` | Automated WoW performance audit | newrelic, agent |

## Tests

### By Tool
- `tests/graphql/` — GraphQL format, variables, validation, file loading
- `tests/elastic/` — Elasticsearch JSON, multiline, validation
- `tests/splunk/` — Cookie auth, browser mimic, search
- `tests/code/` — Java AST, Spring annotations

### Engine Tests
- `tests/engine/` — Conditionals, loops, sub-recipes, numeric logic

### Integration Tests
- `tests/integration/graphql-complex-test.md` — 11 variables, 5 levels deep
- `tests/integration/local-docker-test.md` — Local Docker stack
- `tests/integration/public-api-test.md` — Public API validation
- `tests/integration/multimodal-test.md` — Multi-modal test

## Templates

| Recipe | Description |
|--------|-------------|
| `recipe-template-all-scenarios.md` | Complete reference with all tool operations |
| `meta-gen.md` | Agent-powered recipe generation |

## Demos

Showcases and tutorials for learning the platform.
