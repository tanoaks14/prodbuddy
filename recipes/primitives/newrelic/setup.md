---
name: nr-common-setup
description: Shared steps for New Relic dashboard selection
tags: [common, newrelic]
---

## ask-dashboard-name
tool: interactive
operation: ask
prompt: "What is the name of the New Relic dashboard you are looking for? (Partial names work)"

## search-dashboards
tool: newrelic
operation: list_dashboards
name: "${ask-dashboard-name.answer}"

## extract-dashboard-list
tool: json
operation: extract
data: "${search-dashboards.body}"
paths:
  list: "data.actor.entitySearch.results.entities"

## select-dashboard
tool: interactive
operation: select
data: "${extract-dashboard-list.list}"
prompt: "Select a dashboard from the list above:"
