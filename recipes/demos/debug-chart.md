# Debug Chart Recipe
## step-create-pdf
tool: pdf
operation: create
path: debug-chart-test.pdf
content: |
  DEBUG PDF WITH CHART
  --------------------
  This should have a chart below this line.
  
  chart_data: 10,20,30,40,50,60,70,80,90,100
  
  End of report.

## step-read-pdf
tool: pdf
operation: read
path: debug-chart-test.pdf
