#!/usr/bin/env bash
# Demonstra no cluster: (1) quando uma copia cai outra assume; (2) o HPA sobe com carga e desce
# quando a carga acaba. Precisa de kubectl apontando para o cluster e metrics-server instalado.
#
# Uso: scripts/elasticity-check.sh <status|failover|load|stop-load> [namespace]
set -euo pipefail

CMD="${1:?uso: elasticity-check.sh <status|failover|load|stop-load> [namespace]}"
NS="${2:-shop}"

status() {
  kubectl -n "$NS" get hpa
  kubectl -n "$NS" get pods -l app=backend,track=stable -o wide
  kubectl get nodes
}

failover() {
  # Sonda DENTRO do cluster: bate no Service do backend 1x por segundo durante 60 s.
  kubectl -n "$NS" delete pod probe --ignore-not-found --wait=true >/dev/null
  # shellcheck disable=SC2016  # as expressoes devem ser avaliadas dentro do pod, nao aqui
  kubectl -n "$NS" run probe --image=busybox:1.36 --restart=Never -- /bin/sh -c '
    ok=0; fail=0
    for i in $(seq 1 60); do
      if wget -q -T 2 -O /dev/null http://backend:8080/actuator/health/readiness; then ok=$((ok+1)); else fail=$((fail+1)); fi
      sleep 1
    done
    echo "RESULTADO ok=$ok falhas=$fail"' >/dev/null
  sleep 10
  victim="$(kubectl -n "$NS" get pods -l app=backend,track=stable -o jsonpath='{.items[0].metadata.name}')"
  echo "Derrubando o pod $victim"
  kubectl -n "$NS" delete pod "$victim" --wait=false
  kubectl -n "$NS" wait --for=jsonpath='{.status.phase}'=Succeeded pod/probe --timeout=120s >/dev/null
  kubectl -n "$NS" logs probe | tail -n 1
  kubectl -n "$NS" delete pod probe >/dev/null
  echo "Esperado: falhas=0 ou perto de 0 (a outra copia atendeu enquanto a nova subia)."
}

load() {
  local workers="${WORKERS:-6}"
  for i in $(seq 1 "$workers"); do
    kubectl -n "$NS" delete pod "load-$i" --ignore-not-found --wait=false >/dev/null
    kubectl -n "$NS" run "load-$i" --image=busybox:1.36 --restart=Never --labels=role=load -- /bin/sh -c \
      'while true; do wget -q -O /dev/null http://backend:8080/v3/api-docs; done' >/dev/null
  done
  echo "$workers geradores de carga rodando. Acompanhe: kubectl -n $NS get hpa -w"
  echo "Quando terminar: scripts/elasticity-check.sh stop-load $NS (depois a escala desce sozinha em ~5 min)."
}

stop_load() {
  kubectl -n "$NS" delete pod -l role=load --wait=false
}

case "$CMD" in
  status) status ;;
  failover) failover ;;
  load) load ;;
  stop-load) stop_load ;;
  *) echo "comando invalido: $CMD" >&2; exit 2 ;;
esac
