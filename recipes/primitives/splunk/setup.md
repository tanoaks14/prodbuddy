---
name: splunk-common-setup
description: Shared steps for Splunk authentication and session setup
tags: [common, splunk]
---

## ask-splunk-sid
tool: interactive
operation: ask
prompt: "Please provide your Splunk SID or session cookie to begin:"

## validate-splunk
tool: splunk
operation: list_apps
cookie: "${ask-splunk-sid.answer}"
stopOnFailure: true
