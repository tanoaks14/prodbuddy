#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -x "/usr/libexec/java_home" ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JDK 25 not found. Set JAVA_HOME to a JDK 25 installation." >&2
  exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

export AGENT_ENABLED="true"
export AGENT_PROVIDER="${AGENT_PROVIDER:-ollama}"
export AGENT_BASE_URL="${AGENT_BASE_URL:-http://localhost:11434}"
export AGENT_MODEL="${AGENT_MODEL:-gemma4:e4b}"
export AGENT_CHAT_PATH="${AGENT_CHAT_PATH:-/api/generate}"
export AGENT_AUTH_ENABLED="${AGENT_AUTH_ENABLED:-false}"

echo "Starting ProdBuddy with local LLM settings..."
echo "JAVA_HOME=$JAVA_HOME"
echo "AGENT_PROVIDER=$AGENT_PROVIDER model=$AGENT_MODEL baseUrl=$AGENT_BASE_URL"

if [[ $# -gt 0 ]]; then
  prompt="$*"
else
  prompt="Give me a short health summary of the available tools."
fi

mvn -q -f pom.xml -pl prodbuddy-app -am -DskipTests install
mvn -q -f prodbuddy-app/pom.xml exec:java "-Dexec.args=$prompt"