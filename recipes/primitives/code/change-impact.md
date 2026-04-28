---
name: change-impact
description: Analyzes blast radius of a change
tags: [codecontext, impact]
---

## step-1-build-graph
tool: codecontext
operation: build_graph_db
projectPath: D:\apps\java\app\src\main\java
dbPath: .prodbuddy/java-ast-db

## step-2-change-impact
tool: codecontext
operation: change_impact
dbPath: .prodbuddy/java-ast-db
className: features.aartis.AartiService
maxDepth: 3
