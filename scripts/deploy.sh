#!/usr/bin/env bash
# Aplica o overlay de um ambiente com as imagens indicadas.
# Uso: scripts/deploy.sh <staging|production> <backend-image:tag> <frontend-image:tag> <gateway-image:tag>
#
# staging    : aplica tudo, backend novo incluido (rolling update).
# production : aplica a configuracao e o frontend novo, mas MANTEM o backend na imagem que ja esta
#              no ar. O backend novo entra depois, pelo canario (scripts/canary.sh). Assim um
#              "apply" nunca troca o backend de producao sem passar pelo canario.
set -euo pipefail

ENVIRONMENT="${1:?uso: deploy.sh <staging|production> <backend-image:tag> <frontend-image:tag> <gateway-image:tag>}"
BACKEND_IMAGE="${2:?informe a imagem do backend}"
FRONTEND_IMAGE="${3:?informe a imagem do frontend}"
GATEWAY_IMAGE="${4:?informe a imagem do gateway}"

case "$ENVIRONMENT" in
  staging) NS=shop-staging ;;
  production) NS=shop ;;
  *) echo "ambiente invalido: $ENVIRONMENT (use staging ou production)" >&2; exit 2 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d "$ROOT/k8s/.deploy-XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

BACKEND_APPLY="$BACKEND_IMAGE"
if [ "$ENVIRONMENT" = "production" ]; then
  LIVE="$(kubectl -n "$NS" get deployment backend \
    -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || true)"
  BACKEND_APPLY="${LIVE:-$BACKEND_IMAGE}"   # primeiro deploy: nao ha versao no ar ainda
fi

# As imagens precisam ter tag explicita (nome:tag)
cat > "$WORK/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../overlays/$ENVIRONMENT
images:
  - name: ghcr.io/example/shop-backend
    newName: ${BACKEND_APPLY%:*}
    newTag: "${BACKEND_APPLY##*:}"
  - name: ghcr.io/example/shop-frontend
    newName: ${FRONTEND_IMAGE%:*}
    newTag: "${FRONTEND_IMAGE##*:}"
  - name: ghcr.io/example/shop-gateway
    newName: ${GATEWAY_IMAGE%:*}
    newTag: "${GATEWAY_IMAGE##*:}"
EOF

kubectl apply -k "$WORK"
kubectl -n "$NS" rollout status deployment/frontend --timeout=300s
kubectl -n "$NS" rollout status deployment/backend --timeout=600s
kubectl -n "$NS" rollout status deployment/gateway --timeout=300s
echo "Deploy em $ENVIRONMENT concluido (backend: $BACKEND_APPLY, frontend: $FRONTEND_IMAGE)."
