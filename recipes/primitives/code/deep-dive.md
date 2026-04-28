---
name: code-deep-dive
description: Deep code analysis starting from a search term.
tags: java, codecontext, security
---

## Step 1: Initialize Analysis
tool: codecontext
operation: refresh_graph_db
projectPath: ${projectPath}
dbPath: ${dbPath}

## Step 2: Semantic Search
tool: codecontext
operation: search
projectPath: ${projectPath}
query: ${searchTerm}

## Step 3: Structural Context
tool: codecontext
operation: incident_report
projectPath: ${projectPath}
dbPath: ${dbPath}
query: ${searchTerm}

## Step 4: Complexity Audit
tool: codecontext
operation: complexity_report
dbPath: ${dbPath}
topN: 10

## Step 5: Log Pattern Synthesis
tool: recipe
operation: run
name: log-pattern-synthesis
search_query: ${searchTerm}

## Step 6: Agent Analysis
tool: agent
operation: think
prompt: |
  Based on the code signals, complexity hotspots, and the synthesized log patterns:
  ${Step 5.result}
  
  Critically analyze the problem. Is this a code-level bug, a configuration issue, or a resource bottleneck?
  Look at the call chain: ${Step 3.rankedFindings[0].upstreamChain}
  
  Suggest a better architectural pattern to handle this.
