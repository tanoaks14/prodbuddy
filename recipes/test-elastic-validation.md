---
name: test-elastic-validation
description: Test elasticsearch validation operation and multi-line check
tags: [test]
---

## validate-correct
tool: elasticsearch
operation: validate
index: logs
body: '{"query":{"match_all":{}}}'

## validate-incorrect-data
tool: elasticsearch
operation: validate
index: logs
data: '{"query":{"match_all":{}}}'
condition: true # Always run this to see the failure in context

## validate-multiline-map
tool: elasticsearch
operation: validate
index: logs
body:
  {
    "query": {
      "match_all": {}
    }
  }
condition: true
