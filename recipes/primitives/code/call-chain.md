---
name: call-chain-demo
description: In-depth analysis of a method's dependencies and usage context
tags: [codecontext, call-chain, diagnostic]
---

## step-1-refresh-graph
tool: codecontext
operation: refresh_graph_db
projectPath: .
dbPath: .prodbuddy/java-ast-db

## step-2-verify-id
tool: codecontext
operation: query_graph
dbPath: .prodbuddy/java-ast-db
sql: SELECT id FROM MethodNode WHERE id LIKE '%JavaCodeContextTool#execute%'

## step-3-trace-callees
tool: codecontext
operation: call_chain
dbPath: .prodbuddy/java-ast-db
startMethodId: com.prodbuddy.tools.codecontext.JavaCodeContextTool#execute(ToolRequest,ToolContext)
direction: DOWN
maxDepth: 3

## step-4-trace-callers
tool: codecontext
operation: call_chain
dbPath: .prodbuddy/java-ast-db
startMethodId: com.prodbuddy.tools.codecontext.JavaCodeContextTool#execute(ToolRequest,ToolContext)
direction: UP
maxDepth: 2
