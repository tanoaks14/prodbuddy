## step-1-cookie-based-search
tool: splunk
operation: oneshot
description: Perform a Splunk oneshot search using a session cookie for authentication.
authMode: cookie
cookie: splunkd_8089=hL8_Xv9Q_abc123_DEF456_ghi789
search: index=main | head 5
outputMode: json
