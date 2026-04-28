---
name: complexity-heatmap
description: Heatmap of complex classes
tags: [codecontext, complexity]
---

## step-1-build-graph
tool: codecontext
operation: build_graph_db
projectPath: D:\apps\java\app\src\main\java
dbPath: .prodbuddy/java-ast-db

## step-2-complexity
tool: codecontext
operation: complexity_report
dbPath: .prodbuddy/java-ast-db
topN: 15
