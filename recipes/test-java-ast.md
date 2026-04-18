---
name: test-java-ast
description: Validates the Java AST parser and knowledge graph against the D:\apps\java Spring Boot codebase. Tests real searches for routing, caching, and datasource handling.
tags: [test, codecontext, ast, query, spring]
---

## step-1-build-graph
tool: codecontext
operation: build_graph_db
projectPath: D:\apps\java\app\src\main\java
dbPath: .prodbuddy/java-ast-db

## step-2-project-summary
tool: codecontext
operation: summary
projectPath: D:\apps\java\app\src\main\java

## step-3-search-datasource
tool: codecontext
operation: search
projectPath: D:\apps\java\app\src\main\java
query: DataSource routing master slave

## step-4-search-cache
tool: codecontext
operation: search
projectPath: D:\apps\java\app\src\main\java
query: CacheEviction scheduler invalidate

## step-5-query-classes
tool: codecontext
operation: query_graph
dbPath: .prodbuddy/java-ast-db
sql: SELECT name, fqn, filePath FROM ClassNode ORDER BY name LIMIT 20

## step-6-query-methods-on-service
tool: codecontext
operation: query_graph
dbPath: .prodbuddy/java-ast-db
sql: SELECT name, classFqn, signature FROM MethodNode WHERE classFqn LIKE '%Service%' ORDER BY classFqn LIMIT 20

## step-7-inheritance-graph
tool: codecontext
operation: query_graph
dbPath: .prodbuddy/java-ast-db
sql: SELECT childId, parentId, relationType FROM Inherits LIMIT 20

## step-8-incident-report
tool: codecontext
operation: incident_report
projectPath: D:\apps\java\app\src\main\java
dbPath: .prodbuddy/java-ast-db
query: "Database connection failures - how does the RoutingDataSource switch between master and slave?"
