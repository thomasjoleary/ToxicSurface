#!/bin/bash
# SessionStart hook: warm the Gradle/NeoForge build so compile, run, and
# GameTest tasks are ready in Claude Code on the web sessions.
#
# Requires network access to maven.neoforged.net and the Gradle plugin portal.
# The warmup is intentionally NON-FATAL: if the environment's network policy
# blocks those hosts, the session still starts (we just log a warning).
set -uo pipefail

# Only run in the remote (web) environment.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}"

echo "[session-start] Warming Gradle + NeoForge build (compileJava)..."
if ./gradlew --no-daemon --console=plain compileJava; then
  echo "[session-start] Build warm: dependencies resolved and sources compiled."
else
  echo "[session-start] WARNING: Gradle warmup failed (likely blocked network to"
  echo "[session-start] maven.neoforged.net). The session will still start; run"
  echo "[session-start] './gradlew compileJava' manually once network access is available."
fi
