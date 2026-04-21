---
name: report-demo
description: Generates a PDF report with a bar chart.
tags: [test, pdf]
---

## step-gen-report
tool: pdf
operation: create
path: diagnostic-report.pdf
content: |
  INCIDENT ANALYSIS SUMMARY
  -------------------------
  Incident ID: INC-9982
  Status: Resolved
  
  The system detected a spike in error rates at 10:00 AM.
  Root cause analysis points to a database connection pool exhaustion.
  
  ERROR VOLUME TREND:
  chart_data: 5, 12, 45, 90, 30, 10
  
  The above chart shows the error count peaking at 90 events per minute.
