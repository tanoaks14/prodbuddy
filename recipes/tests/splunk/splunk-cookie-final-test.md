---
name: splunk-cookie-final-test
description: Verifying that user-provided cookies are treated as final.
---

## step-1-cookie-override
tool: splunk
operation: oneshot
authMode: cookie
cookie: "bad_cookie_final_test"
search: |
  index=main "login" | head 1
