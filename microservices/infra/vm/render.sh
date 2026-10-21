#!/usr/bin/env bash
# Gera os arquivos que dependem do dominio e das senhas, para rodar em uma VM:
#   infra/vm/generated/realm.json         realm do Keycloak com os redirects do dominio e senhas dos usuarios
#   infra/vm/generated/shell-config.json  configuracao lida pelo shell em tempo de execucao
#   infra/vm/generated/.env               variaveis do Compose (DOMAIN, ACME_EMAIL, senhas)
# Uso: DOMAIN=loja.exemplo.com ACME_EMAIL=ops@exemplo.com infra/vm/render.sh
# Senhas nao informadas sao geradas ao acaso e gravadas SOMENTE em generated/.env (pasta fora do git).
set -euo pipefail

DOMAIN="${DOMAIN:?defina DOMAIN (ex.: loja.exemplo.com)}"
ACME_EMAIL="${ACME_EMAIL:?defina ACME_EMAIL (aviso de expiracao do certificado)}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/infra/vm/generated"
REALM_SRC="$ROOT/../infra/keycloak/shop-realm.json"

gen() { openssl rand -base64 24 | tr -d '/+=' | cut -c1-24; }
KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-$(gen)}"
DB_PASSWORD="${DB_PASSWORD:-$(gen)}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-$(gen)}"
ALICE_PASSWORD="${ALICE_PASSWORD:-$(gen)}"
BOB_PASSWORD="${BOB_PASSWORD:-$(gen)}"

mkdir -p "$OUT"
umask 077

# Realm: redirects do dominio (remove os de localhost) e senhas dos usuarios de exemplo
sed \
  -e "s#\"http://localhost:4200/\*\", \"https://localhost:8443/\*\"#\"https://$DOMAIN/*\"#" \
  -e "s#\"http://localhost:4200\", \"https://localhost:8443\"#\"https://$DOMAIN\"#" \
  -e "s#http://localhost:4200/\*\#\#https://localhost:8443/\*#https://$DOMAIN/*#" \
  -e "/\"username\": \"alice\"/,/\"credentials\"/ s#\"value\": \"alice\"#\"value\": \"$ALICE_PASSWORD\"#" \
  -e "s#\"type\": \"password\", \"value\": \"alice\"#\"type\": \"password\", \"value\": \"$ALICE_PASSWORD\"#" \
  -e "s#\"type\": \"password\", \"value\": \"bob\"#\"type\": \"password\", \"value\": \"$BOB_PASSWORD\"#" \
  "$REALM_SRC" > "$OUT/realm.json"

if grep -q "localhost" "$OUT/realm.json"; then
  echo "AVISO: sobrou 'localhost' no realm gerado; confira $OUT/realm.json" >&2
fi

cat > "$OUT/shell-config.json" <<JSON
{
  "keycloakUrl": "https://auth.$DOMAIN",
  "realm": "shop",
  "clientId": "shop-frontend"
}
JSON

cat > "$OUT/.env" <<ENV
DOMAIN=$DOMAIN
ACME_EMAIL=$ACME_EMAIL
KC_ADMIN_PASSWORD=$KC_ADMIN_PASSWORD
DB_PASSWORD=$DB_PASSWORD
GRAFANA_PASSWORD=$GRAFANA_PASSWORD
ENV

cat <<MSG
Arquivos gerados em $OUT
  usuario alice: $ALICE_PASSWORD
  usuario bob (admin): $BOB_PASSWORD
  admin do Keycloak e do Grafana, senha do banco: em $OUT/.env
Suba com:
  docker compose --env-file infra/vm/generated/.env -f docker-compose.yml -f docker-compose.vm.yml up -d --build --wait
MSG
