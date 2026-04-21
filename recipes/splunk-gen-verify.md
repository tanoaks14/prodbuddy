---
name: splunk-gen-verify
description: Testing the automated recipe generator.
---

## step-1-generate
tool: agent
operation: generate_recipe
objective: "Create a 2-step recipe. Step 1: Login to Splunk using credentials. Step 2: Run a oneshot search for 'error' in index=main using the cookie from step 1."
