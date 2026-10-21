#!/usr/bin/env bash
# Canario de UM microsservico, com rollback automatico guiado pelo SLO.
#
# Uso: PROM_URL=http://prometheus:9090 NAMESPACE=shop scripts/canary-service.sh <servico> <imagem:tag>
#   ex.: scripts/canary-service.sh order-service ghcr.io/org/shop-order-service:abc123
#
# Como divide o trafego: o Deployment "<servico>-canary" tem os mesmos pods "app=<servico>" do estavel,
# entao o Service os trata como um grupo so e o trafego segue a proporcao de copias. Para ~W% no canario
# com E copias estaveis prontas: canario = ceil(E * W / (100 - W)). E aproximado: nao e um corte exato
# (para peso exato por requisicao use malha de servico ou Gateway API). Etapas: 10% > 25% > 50%.
#
# Falha fechada, como o canary.sh do monolito: erro 5xx acima do limite, SEM metricas do canario, ou
# qualquer comando que falhe => desfaz (canario volta a 0 copias) e termina com erro.
set -euo pipefail

SVC="${1:?uso: canary-service.sh <servico> <imagem:tag>}"
IMAGE="${2:?informe a imagem (ex.: ghcr.io/org/shop-${SVC}:sha)}"
NS="${NAMESPACE:-shop}"
PROM_URL="${PROM_URL:?defina PROM_URL}"
STEPS="${STEPS:-10 25 50}"
SOAK_SECONDS="${SOAK_SECONDS:-300}"
MAX_ERROR_RATIO="${MAX_ERROR_RATIO:-0.01}"
ALLOW_NO_DATA="${ALLOW_NO_DATA:-false}"

case "$SVC" in
  catalog-service | order-service | notification-service) ;;
  *) echo "servico invalido: $SVC" >&2; exit 2 ;;
esac

PROMOTED=false

# Roda sempre ao sair: se nao terminou promovido, desfaz o canario.
finish() {
  local code=$?
  if [ "$PROMOTED" != "true" ]; then
    echo "Canario de $SVC NAO concluido (codigo $code): desfazendo" >&2
    kubectl -n "$NS" scale "deployment/$SVC-canary" --replicas=0 || true
  fi
  exit "$code"
}
trap finish EXIT

stable_ready() {
  kubectl -n "$NS" get "deployment/$SVC" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true
}

# Quantas copias de canario dao ~W% (arredonda para cima, minimo 1).
canary_replicas() {
  awk -v e="$1" -v w="$2" 'BEGIN { c = e * w / (100 - w); n = int(c); if (c > n) n++; if (n < 1) n = 1; print n }'
}

# Taxa de 5xx do canario, ou "nodata" se o Prometheus nao tem a serie.
error_ratio() {
  local q
  q='sum(rate(http_server_requests_seconds_count{namespace="'"$NS"'",pod=~"'"$SVC"'-canary.*",uri=~"/api.*",status=~"5.."}[2m])) / sum(rate(http_server_requests_seconds_count{namespace="'"$NS"'",pod=~"'"$SVC"'-canary.*",uri=~"/api.*"}[2m]))'
  curl -fsS --max-time 20 -G "$PROM_URL/api/v1/query" --data-urlencode "query=$q" \
    | jq -r '.data.result[0].value[1] // "nodata"'
}

STABLE="$(stable_ready)"
STABLE="${STABLE:-0}"
if [ "$STABLE" -lt 1 ]; then
  echo "o estavel $SVC nao tem copias prontas: nada para comparar, abortando" >&2
  exit 1
fi

echo "Canario de $SVC com $IMAGE (estavel: $STABLE copias prontas)"
kubectl -n "$NS" set image "deployment/$SVC-canary" "$SVC=$IMAGE"

for weight in $STEPS; do
  replicas="$(canary_replicas "$STABLE" "$weight")"
  echo "Alvo ~${weight}% do trafego: $replicas copia(s) de canario"
  kubectl -n "$NS" scale "deployment/$SVC-canary" --replicas="$replicas"
  kubectl -n "$NS" rollout status "deployment/$SVC-canary" --timeout=300s
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

echo "Promovendo $IMAGE para a faixa estavel de $SVC"
kubectl -n "$NS" set image "deployment/$SVC" "$SVC=$IMAGE"
if ! kubectl -n "$NS" rollout status "deployment/$SVC" --timeout=600s; then
  echo "ROLLBACK: o rollout do estavel falhou; voltando a versao anterior" >&2
  kubectl -n "$NS" rollout undo "deployment/$SVC"
  exit 1
fi
PROMOTED=true
kubectl -n "$NS" scale "deployment/$SVC-canary" --replicas=0
echo "Canario de $SVC concluido."
