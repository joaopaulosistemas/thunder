#!/usr/bin/env bash
# Publica um resumo da falha de um passo do CI como comentario do commit, para quem desenvolve (ou um agente)
# ler sem baixar os logs. Uso: scripts/ci-report-failure.sh <arquivo-de-log> <titulo>
# Precisa de GH_TOKEN e do GITHUB_REPOSITORY/GITHUB_SHA do Actions; o job precisa de "contents: write".
set -euo pipefail

LOG="${1:?arquivo de log}"
TITLE="${2:?titulo}"
MAX=60000

{
  echo "### CI falhou: ${TITLE}"
  echo
  echo "Execucao: ${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"
  echo
  echo '```'
  # Erros do Maven, testes que falharam e causas (Caused by), sem repetir linhas identicas em sequencia
  grep -E '^\[ERROR\]|Tests run:.*(FAIL|ERROR)|Caused by:|^npm (error|ERR)|^Error:|FAILED|error TS[0-9]+' "$LOG" | uniq | head -n 200
  echo '```'
} | head -c "$MAX" > /tmp/ci-report.md

gh api "repos/${GITHUB_REPOSITORY}/commits/${GITHUB_SHA}/comments" -f body="$(cat /tmp/ci-report.md)" > /dev/null
echo "Resumo da falha publicado no commit ${GITHUB_SHA}"
