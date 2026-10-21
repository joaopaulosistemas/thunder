#!/usr/bin/env bash
# Teste de fumaca de um ambiente: backend saudavel e seguranca ativa.
# Uso: scripts/smoke.sh <namespace>
set -euo pipefail

NS="${1:?uso: smoke.sh <namespace>}"
PORT="${LOCAL_PORT:-18080}"

kubectl -n "$NS" port-forward deployment/gateway "$PORT:8080" >/dev/null 2>&1 &
PF=$!
trap 'kill "$PF" 2>/dev/null || true' EXIT

for _ in $(seq 1 30); do
  curl -fsS "http://localhost:$PORT/actuator/health/readiness" >/dev/null 2>&1 && break
  sleep 2
done

health="$(curl -fsS "http://localhost:$PORT/actuator/health/readiness")"
if ! echo "$health" | grep -Eq '"status" ?: ?"UP"'; then
  echo "gateway nao esta UP: $health" >&2
  exit 1
fi

code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/api/products")"
if [ "$code" != "401" ]; then
  echo "esperava 401 sem token em /api/products, veio $code (API desprotegida?)" >&2
  exit 1
fi

echo "Smoke OK ($NS): gateway UP e API protegida na borda (401 sem token)."
