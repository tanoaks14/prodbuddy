---
name: test-elastic-json
description: Test elastic json extract
tags: [test]
---

## search-elastic
tool: elasticsearch
operation: search
index: logs
authEnabled: false
noTruncate: true
data: '{"query":{"match_all":{}}}'

## extract-json
tool: json
operation: extract
data: "${search-elastic.body}"
paths:
  hits: "hits.hits"
