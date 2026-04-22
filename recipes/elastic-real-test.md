## step-1-query-elastic
tool: elasticsearch
operation: search
index: logs
query:
  match_all: {}

## step-2-extract-location
tool: json
operation: extract
data: ${step-1-query-elastic.body}
paths:
  location: hits.hits[0]._source.kel_location
