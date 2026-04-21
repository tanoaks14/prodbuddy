## step-1-splunk
tool: splunk-legacy-broken
operation: oneshot
search: "| stats count from index=payment where status=500"

## step-2-newrelic
tool: newrelic
operation: query
metric: average_response_time

## step-3-validate
tool: agent
operation: validate_recipe
recipe: ${system.current_recipe}
