#!/usr/bin/env bash
# Configura a protecao da branch main no GitHub: PR obrigatorio, aprovacao dos donos
# (CODEOWNERS), CI verde e sem push direto. Isso e configuracao do repositorio, nao do codigo,
# por isso e um script para voce rodar uma vez (precisa de gh autenticado como administrador).
# Uso: scripts/protect-main.sh <dono/repositorio>
set -euo pipefail

REPO="${1:?uso: protect-main.sh <dono/repositorio>}"

gh api -X PUT "repos/$REPO/branches/main/protection" --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["backend", "gateway", "services", "web", "frontend", "manifests", "e2e"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "require_code_owner_reviews": true,
    "dismiss_stale_reviews": true
  },
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "restrictions": null
}
JSON

echo "Protecao aplicada a main em $REPO."
