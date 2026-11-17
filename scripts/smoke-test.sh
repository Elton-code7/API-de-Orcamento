#!/usr/bin/env bash
# Requer API em execução, curl e python3. Consome a API de IA.
# Usa dados fictícios e CANCELA a proposta; não registra movimentação.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then set -a; source .env; set +a; fi
: "${APP_API_KEY:?Defina APP_API_KEY}"
base="${BASE_URL:-http://localhost:8080}"
response=$(curl --fail-with-body -sS "$base/api/commands/text" -H "X-API-Key: $APP_API_KEY" -H 'Content-Type: application/json'   -d '{"text":"Prepare uma despesa de 35,50 reais, descrição almoço de teste, categoria alimentação, em 17 de novembro de 2026."}')
printf '%s\n' "$response" | python3 -m json.tool
id=$(printf '%s' "$response" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["status"]=="AGUARDANDO_CONFIRMACAO",d; print(d["id"])')
curl --fail-with-body -sS -X POST "$base/api/commands/$id/cancel" -H "X-API-Key: $APP_API_KEY" | python3 -m json.tool
curl --fail-with-body -sS "$base/api/commands/$id/audit" -H "X-API-Key: $APP_API_KEY" | python3 -m json.tool
