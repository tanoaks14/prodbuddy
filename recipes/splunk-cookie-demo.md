---
name: splunk-cookie-demo
description: Demonstrates Splunk search using included authentication
tags: [splunk, demo, include]
---

## authentication
tool: recipe
operation: include
path: "./common/splunk-setup.md"

## step-search
tool: splunk
operation: oneshot
authMode: cookie
cookie: "${ask-splunk-sid.answer}"
search: index=main | head 5
outputMode: json
