---
name: dead-code-check
description: Detects unreachable / dead code in the project
tags: [codecontext, deadcode]
---

## step-1-build-graph
tool: codecontext
operation: build_graph_db
projectPath: D:\apps\java\app\src\main\java
dbPath: .prodbuddy/java-ast-db

## step-2-dead-code
tool: codecontext
operation: dead_code
dbPath: .prodbuddy/java-ast-db
