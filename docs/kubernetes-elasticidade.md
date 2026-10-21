# Backend em Kubernetes: elástico e com alta disponibilidade

Dois problemas, duas camadas.

| Pergunta | Camada | Mecanismo | Arquivo |
|---|---|---|---|
| Se uma cópia cai, outra assume? | pods | 2+ cópias em máquinas/zonas diferentes, probes, Service só envia para pods prontos | `k8s/base/backend.yaml` |
| Muita chamada: sobe mais cópias? Pouca: tira? | pods | HPA (CPU 65%, sobe rápido, desce 1 por minuto após 5 min calmos) | `k8s/base/hpa.yaml` |
| Manutenção/troca de máquina derruba tudo? | pods | PodDisruptionBudget: no máximo 1 cópia fora por vez | `k8s/base/pdb.yaml` |
| Pouca chamada: desligar a máquina? | nós | Karpenter (ou Cluster Autoscaler) consolida e remove máquinas vazias | `k8s/optional/karpenter-nodepool.yaml` |

## Como funciona

**Falha.** O backend roda com no mínimo 2 cópias, espalhadas por máquina e por zona (`topologySpreadConstraints` e anti-afinidade). Se uma máquina morre, a outra cópia continua respondendo e o Kubernetes recria a que faltava em outro nó. A readiness probe tira do Service qualquer cópia que não responde, e a liveness probe reinicia a que travou.

**Parada limpa.** Ao remover uma cópia (deploy, scale-down, drain), o pod espera 10 s (`preStop`) para sair da rotação do Service e do Ingress, depois o Spring termina as requisições em andamento (`server.shutdown: graceful`, até 30 s). Os 60 s de `terminationGracePeriodSeconds` cobrem os dois. Sem isso, parte das requisições receberia erro a cada scale-down.

**Subir e descer.** O HPA compara o uso de CPU com o `request` do container (250m). Acima de 65% ele adiciona cópias, podendo dobrar por minuto. Quando a carga cai ele espera 5 minutos e remove uma cópia por minuto, até o mínimo. Produção: 2 a 10 cópias. Staging: 1 a 3 (overlay).

**Desligar máquinas.** Remover pods não reduz a conta de nuvem; é preciso remover os nós. Com Karpenter, nós vazios ou subutilizados são consolidados após 2 minutos e desligados, e novos nós são criados quando um pod fica sem lugar. O PDB garante que isso aconteça uma cópia por vez.

## Por que não zerar as cópias

O mínimo é 2, não 0. Com 0 cópias a primeira requisição espera a JVM subir (dezenas de segundos), e o consumidor Kafka de `notifications` deixaria de ler eventos. Para economizar fora do horário comercial, reduza o mínimo apenas em staging. Escala a zero só faria sentido separando o consumidor em outro serviço.

## Pré-requisitos no cluster

- `metrics-server` (o HPA precisa dele).
- Pelo menos 2 nós, de preferência em 2 ou 3 zonas.
- Opcional: Karpenter ou Cluster Autoscaler para os nós; KEDA se quiser escalar por requisições por segundo (`k8s/optional/keda-backend.yaml`, no lugar do HPA do backend).

## Como conferir

```bash
scripts/elasticity-check.sh status             # HPA, pods e nós
scripts/elasticity-check.sh failover           # derruba um pod durante 60 s de sondagem
WORKERS=6 scripts/elasticity-check.sh load     # gera carga; acompanhe: kubectl get hpa -w
scripts/elasticity-check.sh stop-load          # encerra a carga; a escala desce em ~5 min
```

## Limites desta configuração

- O alvo de 65% de CPU e as faixas 2-10 são pontos de partida; ajuste com teste de carga real.
- O gerador de carga bate em `/v3/api-docs` (público); não representa o custo de um pedido real.
- O PDB cobre remoções voluntárias. Queda súbita de uma máquina tira as cópias dela até o Kubernetes recriá-las; por isso o mínimo é 2 em máquinas diferentes.
- Estado compartilhado (Postgres, Redis, Kafka) continua fora do cluster, em serviços gerenciados.
