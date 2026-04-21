#!/usr/bin/env bash

set -euo pipefail

NAME="${1:-Master Platform Audit}"
PROMPT='Create a high-complexity investigation recipe. Step 1: Use splunk.oneshot to search for 500 errors in payment-service. Step 2: Use newrelic.query to get average response time for the service. Step 3: Use elasticsearch.search to find transaction traces in logs. Step 4: Use codecontext.incident_report for query "payment degradation". Step 5: Use http.get to check gateway status. Step 6: Use agent.validate_recipe to check this playbook. Use ${stepName.field} for chaining.'

mvn -f prodbuddy-app/pom.xml exec:java "-Dexec.args=--generate-recipe \"$NAME\" \"$PROMPT\""
