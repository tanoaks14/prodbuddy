$ErrorActionPreference = 'Stop'

Set-Location $PSScriptRoot

if (Test-Path 'd:\tools\jdk\jdk-25') {
    $env:JAVA_HOME = 'd:\tools\jdk\jdk-25'
}
elseif (-not $env:JAVA_HOME) {
    throw 'JDK 25 not found. Set JAVA_HOME to a JDK 25 installation.'
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$env:AGENT_ENABLED = 'true'
$env:AGENT_PROVIDER = 'ollama'
if (-not $env:AGENT_BASE_URL) { $env:AGENT_BASE_URL = 'http://localhost:11434' }
$env:AGENT_MODEL = 'gemma4:e4b'
if (-not $env:AGENT_CHAT_PATH) { $env:AGENT_CHAT_PATH = '/api/generate' }
if (-not $env:AGENT_AUTH_ENABLED) { $env:AGENT_AUTH_ENABLED = 'false' }

Write-Host "Starting ProdBuddy with local LLM settings..."
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "AGENT_PROVIDER=$env:AGENT_PROVIDER model=$env:AGENT_MODEL baseUrl=$env:AGENT_BASE_URL"

$prompt = if ($args.Length -gt 0) { $args -join ' ' } else { 'Give me a short health summary of the available tools.' }
$installArgs = @(
    '-q',
    '-f',
    'pom.xml',
    '-pl',
    'prodbuddy-app',
    '-am',
    '-DskipTests',
    'install'
)

& mvn @installArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$runArgs = @(
    '-q',
    '-f',
    'prodbuddy-app/pom.xml',
    'exec:java',
    "-Dexec.args=$prompt"
)

& mvn @runArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
