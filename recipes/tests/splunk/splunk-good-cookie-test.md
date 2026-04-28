---
name: splunk-good-cookie-test
description: Verifying end-to-end success of user-provided cookie override.
---

## step-1-get-good-cookie
tool: splunk
operation: login
authMode: user

## step-2-use-good-cookie
tool: splunk
operation: oneshot
authMode: cookie
cookie: "${step-1-get-good-cookie.cookie}"
search: |
  | makeresults | eval auth_status="success_via_overridden_cookie"
