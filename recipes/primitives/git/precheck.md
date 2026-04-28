---
name: git-precheck
description: Uses git diff to find changed files and checks impact
tags: [git, codecontext]
---

## step-1-git-diff
tool: git
operation: diff
repoPath: D:\apps\java

## step-2-build-graph
tool: codecontext
operation: build_graph_db
projectPath: D:\apps\java\app\src\main\java
dbPath: .prodbuddy/java-ast-db

## step-3-impact-check
tool: codecontext
operation: change_impact
dbPath: .prodbuddy/java-ast-db
className: ${step-1-git-diff.firstChangedClass}
maxDepth: 3
