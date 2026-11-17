#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
: "${OPENAI_API_KEY:?Defina OPENAI_API_KEY no .env ou no ambiente}"
: "${APP_API_KEY:?Defina APP_API_KEY no .env ou no ambiente}"
exec mvn spring-boot:run "$@"
