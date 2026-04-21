$recipes = @("broken-master", "invalid-recipe-test", "master-diagnostic", "comprehensive-demo", "test-java-ast")
$results = @{}

Write-Host "Starting Final Regression Test..." -ForegroundColor Cyan

foreach ($name in $recipes) {
    Write-Host "Running Recipe: $name" -ForegroundColor Yellow
    $cmd = "mvn -f prodbuddy-app/pom.xml exec:java `"-Dexec.args=--run-recipe $name`""
    $output = Invoke-Expression $cmd
    
    if ($LASTEXITCODE -eq 0) {
        $results[$name] = "SUCCESS (Build OK)"
        if ($output -match "Passed: (\d+)") {
            $passed = $matches[1]
            $results[$name] += " - Steps Passed: $passed"
        }
    } else {
        $results[$name] = "FAILED (Build Error)"
    }
}

Write-Host "`n=== Regression Summary ===" -ForegroundColor Green
foreach ($key in $results.Keys) {
    Write-Host "$key : $($results[$key])"
}
