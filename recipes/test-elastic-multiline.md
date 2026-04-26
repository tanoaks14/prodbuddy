---
name: test-elastic-multiline
description: Test elastic multi-line body validator
tags: [test]
---

## validate-recipe
tool: agent
operation: validate_recipe
recipe: |
  ---
  name: inner-test
  description: A recipe with bad formatting
  ---
  
  ## search-elastic
  tool: elasticsearch
  operation: search
  index: logs
  body:
    {
      "query": {
        "match_all": {}
      }
    }
