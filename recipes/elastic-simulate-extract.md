## step-1-simulate-elastic-response
tool: agent
operation: think
prompt: |
  Return ONLY this JSON string: 
  {"hits":{"hits":[{"_source":{"kel_location":"/app/data/keys/prod.pem"}}]}}

## step-2-extract-location
tool: json
operation: extract
data: ${step-1-simulate-elastic-response.opinion}
paths:
  location: hits.hits[0]._source.kel_location
