## step-meta
tool: agent
operation: generate_recipe
name: master-diagnostic
prompt: |
  Write a high-complexity investigation recipe with exactly 6 steps. 
  Step 1: tool: splunk, operation: oneshot, search for 'error 500' in index=payment.
  Step 2: tool: newrelic, operation: query, query response time for app 'payment-api'.
  Step 3: tool: elasticsearch, operation: search, find 'timeout' in index=orders.
  Step 4: tool: codecontext, operation: incident_report, query: 'reproduce payment timeout'.
  Step 5: tool: http, operation: get, url: 'https://gateway.internal/health'.
  Step 6: tool: agent, operation: validate_recipe, recipe: '...'.
  Use ${stepName.field} references to link steps (e.g. use data from Step 1 in Step 2). 
  Return ONLY the recipe markdown.
