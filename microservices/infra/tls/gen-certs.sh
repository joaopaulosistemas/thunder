#!/usr/bin/env bash
# Gera uma CA local e um certificado de servidor para rodar tudo em HTTPS no Docker Compose.
# Saida: infra/tls/certs/{ca.crt,ca.key,server.crt,server.key}. A pasta certs/ nao vai para o git.
# Para o navegador confiar, importe infra/tls/certs/ca.crt como autoridade raiz (ou use mkcert).
# Uso: infra/tls/gen-certs.sh [dias=365]
set -euo pipefail

DAYS="${1:-365}"
DIR="$(cd "$(dirname "$0")" && pwd)/certs"
mkdir -p "$DIR"
cd "$DIR"

if [ -f server.crt ] && openssl x509 -checkend 86400 -noout -in server.crt >/dev/null 2>&1; then
  echo "Certificado ainda valido em $DIR; nada a fazer (apague a pasta para regenerar)."
  exit 0
fi

# 1) CA local (4096 bits, valida por 10 anos)
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
  -subj "/O=Shop Dev/CN=Shop Dev Local CA" -out ca.crt

# 2) Chave e pedido do servidor (ECDSA P-256), com SAN: navegador moderno ignora o CN
openssl ecparam -name prime256v1 -genkey -noout -out server.key
openssl req -new -key server.key -subj "/O=Shop Dev/CN=localhost" -out server.csr

cat > server.ext <<EXT
authorityKeyIdentifier = keyid,issuer
basicConstraints       = CA:FALSE
keyUsage               = digitalSignature
extendedKeyUsage       = serverAuth
subjectAltName         = DNS:localhost, DNS:shop.localtest.me, IP:127.0.0.1, IP:::1
EXT

# 3) A CA assina o certificado do servidor
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days "$DAYS" -sha256 -extfile server.ext

rm -f server.csr server.ext ca.srl
chmod 600 ca.key server.key
chmod 644 ca.crt server.crt   # o nginx do container le como usuario sem privilegios
openssl verify -CAfile ca.crt server.crt
echo "Pronto: $DIR"
