#!/usr/bin/env bash
# Aplica o overlay de microsservicos com TODAS as imagens na mesma tag (o SHA do commit) e espera
# cada Deployment ficar pronto. Rolling update com maxUnavailable=0: se um pod novo nao fica pronto,
# o antigo continua atendendo e o rollout falha (o job para).
# Uso: OWNER=minha-org scripts/deploy-microservices.sh <staging|production> <tag>
#
# staging    : aplica tudo com a tag nova (rolling update).
# production : aplica configuracao e frontends novos, mas MANTEM os 3 servicos (catalog, order,
#              notification) na imagem que ja esta no ar. A imagem nova de cada um entra depois, pelo
#              canario por servico (scripts/canary-service.sh). Assim um "apply" nunca troca um servico
#              de producao sem passar pelo canario. No primeiro deploy nao ha versao no ar: usa a nova.
set -euo pipefail

ENVIRONMENT="${1:?uso: deploy-microservices.sh <staging|production> <tag>}"
TAG="${2:?informe a tag (sha) das imagens}"
OWNER="${OWNER:?defina OWNER (dono do registro ghcr.io)}"

case "$ENVIRONMENT" in
  staging) NS=shop-staging ;;
  production) NS=shop ;;
  *) echo "ambiente invalido: $ENVIRONMENT (use staging ou production)" >&2; exit 2 ;;
esac

APPS="shell mfe-catalog mfe-orders mfe-notifications gateway catalog-service order-service notification-service"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d "$ROOT/microservices/k8s/.deploy-XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# Imagem atualmente no ar de um servico (vazio se ainda nao existe)
live_image() {
  kubectl -n "$NS" get "deployment/$1" -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || true
}

{
  echo "apiVersion: kustomize.config.k8s.io/v1beta1"
  echo "kind: Kustomization"
  echo "resources:"
  echo "  - ../overlays/$ENVIRONMENT"
  echo "images:"
  for app in $APPS; do
    name="ghcr.io/$OWNER/shop-$app"
    tag="$TAG"
    case "$app" in
      catalog-service | order-service | notification-service)
        if [ "$ENVIRONMENT" = "production" ]; then
          live="$(live_image "$app")"
          if [ -n "$live" ]; then
            name="${live%:*}"
            tag="${live##*:}"
          fi
        fi
        ;;
    esac
    echo "  - name: ghcr.io/example/shop-$app"
    echo "    newName: $name"
    echo "    newTag: \"$tag\""
  done
} > "$WORK/kustomization.yaml"

kustomize build "$WORK" | kubectl apply -f -
for app in $APPS; do
  kubectl -n "$NS" rollout status "deployment/$app" --timeout=600s
done
echo "Deploy de microsservicos concluido em $ENVIRONMENT ($TAG)."
