#!/usr/bin/env bash
# Release canario progressivo com rollback automatico guiado pelo SLO.
#
# Uso:  PROM_URL=http://prometheus:9090 ./scripts/canary.sh ghcr.io/org/shop-backend:abc123
#
# Etapas: 5% -> 25% -> 50% -> 100% do trafego /api. Em cada etapa espera a janela de
# observacao e consulta a taxa de erro 5xx do canario no Prometheus.
#
# Falha fechada: qualquer coisa fora do esperado desfaz o canario e termina com erro, inclusive
#  - taxa de erro acima do limite;
#  - SEM METRICAS do canario (Prometheus fora do ar, PodMonitor ausente, sem trafego): nao da para
#    provar que esta saudavel, entao nao promove (ALLOW_NO_DATA=true muda isso, nao recomendado);
#  - qualquer comando que falhe no meio do caminho (trap abaixo).
set -euo pipefail

IMAGE="${1:?informe a imagem do backend (ex.: ghcr.io/org/shop-backend:sha)}"
NS="${NAMESPACE:-shop}"
PROM_URL="${PROM_URL:?defina PROM_URL}"
STEPS="${STEPS:-5 25 50 100}"
SOAK_SECONDS="${SOAK_SECONDS:-300}"
MAX_ERROR_RATIO="${MAX_ERROR_RATIO:-0.01}"   # 1% de 5xx no canario derruba o release
ALLOW_NO_DATA="${ALLOW_NO_DATA:-false}"

PROMOTED=false

set_weight() {
  kubectl -n "$NS" annotate ingress shop-canary \
    "nginx.ingress.kubernetes.io/canary-weight=$1" --overwrite
}

# Roda sempre ao sair. Se o release nao terminou promovido, desfaz o canario.
finish() {
  local code=$?
  if [ "$PROMOTED" != "true" ]; then
    echo "Release NAO concluido (codigo $code): desfazendo o canario" >&2
    set_weight 0 || true
    kubectl -n "$NS" scale deployment backend-canary gateway-canary --replicas=0 || true
  fi
  exit "$code"
}
trap finish EXIT

# Devolve a taxa de 5xx do canario, ou "nodata" se o Prometheus nao tem a serie.
error_ratio() {
  local q
  q='sum(rate(http_server_requests_seconds_count{namespace="'"$NS"'",pod=~"backend-canary.*",uri=~"/api.*",status=~"5.."}[2m])) / sum(rate(http_server_requests_seconds_count{namespace="'"$NS"'",pod=~"backend-canary.*",uri=~"/api.*"}[2m]))'
  curl -fsS --max-time 20 -G "$PROM_URL/api/v1/query" --data-urlencode "query=$q" \
    | jq -r '.data.result[0].value[1] // "nodata"'
}

echo "Subindo canario com $IMAGE"
kubectl -n "$NS" set image deployment/backend-canary backend="$IMAGE"
kubectl -n "$NS" scale deployment backend-canary --replicas=2
kubectl -n "$NS" scale deployment gateway-canary --replicas=1
kubectl -n "$NS" rollout status deployment/backend-canary --timeout=300s
kubectl -n "$NS" rollout status deployment/gateway-canary --timeout=300s

for weight in $STEPS; do
  echo "Peso do canario: ${weight}%"
  set_weight "$weight"
  sleep "$SOAK_SECONDS"
  ratio="$(error_ratio)"
  echo "Taxa de erro 5xx do canario: $ratio (limite $MAX_ERROR_RATIO)"

  if [ "$ratio" = "nodata" ] || [ "$ratio" = "NaN" ]; then
    if [ "$ALLOW_NO_DATA" = "true" ]; then
      echo "AVISO: sem metricas do canario nesta etapa; seguindo por ALLOW_NO_DATA=true" >&2
      continue
    fi
    echo "ROLLBACK: sem metricas do canario para provar que esta saudavel" >&2
    exit 1
  fi

  if awk -v r="$ratio" -v m="$MAX_ERROR_RATIO" 'BEGIN { exit !(r > m) }'; then
    echo "ROLLBACK: taxa de erro $ratio acima do limite $MAX_ERROR_RATIO" >&2
    exit 1
  fi
done

echo "Promovendo $IMAGE para a faixa estavel"
kubectl -n "$NS" set image deployment/backend backend="$IMAGE"
if ! kubectl -n "$NS" rollout status deployment/backend --timeout=600s; then
  echo "ROLLBACK: o rollout da faixa estavel falhou; voltando a versao anterior" >&2
  kubectl -n "$NS" rollout undo deployment/backend
  exit 1
fi
PROMOTED=true
set_weight 0
kubectl -n "$NS" scale deployment backend-canary gateway-canary --replicas=0
echo "Release concluido."
