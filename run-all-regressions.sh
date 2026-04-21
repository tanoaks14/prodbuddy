#!/usr/bin/env bash

set -uo pipefail

RECIPES=("broken-master" "invalid-recipe-test" "master-diagnostic" "comprehensive-demo" "test-java-ast")
declare -A RESULTS

echo -e "\033[0;36mStarting Final Regression Test...\033[0m"

for name in "${RECIPES[@]}"; do
    echo -e "\033[0;33mRunning Recipe: $name\033[0m"
    mvn -f prodbuddy-app/pom.xml exec:java "-Dexec.args=--run-recipe $name"
    
    if [ $? -eq 0 ]; then
        RESULTS[$name]="SUCCESS (Build OK)"
    else
        RESULTS[$name]="FAILED (Build Error)"
    fi
done

echo -e "\n\033[0;32m=== Regression Summary ===\033[0m"
for name in "${RECIPES[@]}"; do
    echo "$name : ${RESULTS[$name]}"
done
