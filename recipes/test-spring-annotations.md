---
name: test-spring-annotations
description: Test Pattern Spring Annotations Verification
tags: [codecontext]
---

## step-1-build-graph
tool: codecontext
operation: build_graph_db
projectPath: D:\apps\java\app\src\main\java
dbPath: .prodbuddy/java-ast-db

## step-2-query-annotations
tool: codecontext
operation: query_graph
dbPath: .prodbuddy/java-ast-db
sql: SELECT classFqn, name, annotations FROM MethodNode WHERE annotations IS NOT NULL AND annotations <> '' LIMIT 10
