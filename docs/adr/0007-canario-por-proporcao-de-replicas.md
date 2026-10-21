# ADR 0007: Canário por serviço usando proporção de réplicas

**Status:** Proposto
**Data:** 2026-10-20
**Decisores:** plataforma e donos dos serviços

## Contexto
Os microsserviços usavam só rolling update. Queremos publicar um serviço por vez, olhar o SLO dele e voltar sozinho se piorar,
sem instalar uma plataforma de entrega progressiva.

## Decisão
Cada serviço tem um Deployment `canary` (0 réplicas) ao lado do estável. O Service seleciona só `app`, então o tráfego se divide
pela proporção de pods (10, 25, 50 %). `scripts/canary-service.sh` sobe o canário, consulta o SLO do serviço no Prometheus a cada
etapa e reverte se falhar ou se não houver dados. O gateway fica fora.

## Opções consideradas

### Opção A: proporção de réplicas (escolhida)
| Dimensão | Avaliação |
|---|---|
| Complexidade | Baixa: Deployments e um script |
| Custo | Baixo, mas o canário consome pods extras |
| Escalabilidade | Média: percentual limitado pelo nº de réplicas |
| Familiaridade da equipe | Alta |

**Prós:** funciona em qualquer cluster. **Contras:** percentual aproximado; não escolhe quem vê o canário; mexe no HPA durante o teste.

### Opção B: Argo Rollouts ou Flagger com divisão de tráfego (Linkerd/ingress)
| Dimensão | Avaliação |
|---|---|
| Complexidade | Média a alta: controlador novo |
| Custo | Médio |
| Escalabilidade | Alta: percentual exato, análise automática |
| Familiaridade da equipe | Baixa |

**Prós:** divisão exata, análise e rollback declarativos. **Contras:** mais um componente no caminho do deploy.

## Análise de trade-offs
Para 3 serviços e uma equipe, o script basta e dá a segurança principal (SLO e rollback). Mudar para a Opção B quando houver
mais de ~6 serviços ou quando precisarmos de canário por usuário ou cabeçalho.

## Consequências
- Mais fácil: publicar um serviço sem tocar nos outros, com reversão automática.
- Mais difícil: com poucas réplicas, 10 % não existe (arredonda); o rollback depende de a métrica existir (sem dado = falha).
- Revisitar: o HPA durante o canário, e o gateway fora do esquema.

## Ações
1. [ ] Testar o script contra um cluster real (hoje só com `kubectl` e `curl` falsos).
2. [ ] Decidir o limite de réplicas mínimo para usar o canário.
