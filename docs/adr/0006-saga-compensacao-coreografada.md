# ADR 0006: Compensação da saga de pedidos sem orquestrador

**Status:** Proposto
**Data:** 2026-10-20
**Decisores:** quem responde por order-service e catalog-service

## Contexto
A saga do ADR 0005 só sabia confirmar ou recusar. Faltavam três caminhos: o catálogo não responder
(o pedido ficava `PENDING` para sempre), o cliente desistir do pedido, e a reserva de estoque chegar
depois de o pedido já estar cancelado ou recusado (estoque preso).

## Decisão
Manter a coreografia e acrescentar compensações pequenas e idempotentes:
- **Timeout:** `PendingOrderReaper` recusa pedidos `PENDING` além de `ORDER_PENDING_TIMEOUT` (2 min), com `for update skip locked`.
- **Cancelamento:** `POST /api/orders/{id}/cancel` emite `orders.cancelled` e, se o pedido estava `CONFIRMED`, `orders.stock-release`.
- **Reserva tardia:** `stock.reserved` para pedido `CANCELLED`/`REJECTED` gera `orders.stock-release`.
- **Idempotência:** a tabela `stock_reservation` garante que o estoque volta uma única vez por pedido.

## Opções consideradas

### Opção A: coreografia com compensações (escolhida)
| Dimensão | Avaliação |
|---|---|
| Complexidade | Média: 2 tópicos e 1 tabela novos |
| Custo | Baixo, sem plataforma nova |
| Escalabilidade | Alta: cada serviço reage a eventos |
| Familiaridade da equipe | Alta, é o padrão que já existe |

**Prós:** sem ponto central de falha; reaproveita outbox e inbox. **Contras:** o fluxo fica espalhado em vários serviços; difícil enxergar o estado de uma saga inteira.

### Opção B: orquestrador (Temporal, Camunda)
| Dimensão | Avaliação |
|---|---|
| Complexidade | Alta: plataforma nova para operar |
| Custo | Médio a alto |
| Escalabilidade | Alta, depende da plataforma |
| Familiaridade da equipe | Baixa |

**Prós:** fluxo visível, timeouts e retentativas nativos. **Contras:** mais uma peça crítica; reescrita do fluxo.

### Opção C: manter só confirmar/recusar
Mais simples, mas deixa pedido preso e estoque perdido. Descartada.

## Análise de trade-offs
Com 3 passos e 2 compensações, o custo de operar um orquestrador supera o ganho de visibilidade. O ponto de virada é crescer para
uns 5 passos ou ter compensações que dependam umas das outras; aí a Opção B passa a compensar.

## Consequências
- Mais fácil: nenhum pedido fica pendente para sempre; estoque não vaza.
- Mais difícil: entender o estado de uma saga exige juntar logs e traces de 3 serviços.
- Revisitar: o timeout fixo de 2 min; um pedido recusado por timeout pode ser reservado depois (a compensação cobre, mas o cliente já foi avisado).

## Ações
1. [ ] Teste de integração (Postgres + Kafka reais) para timeout, cancelamento e reserva tardia.
2. [ ] Alerta `OrdersStuckPending` validado em ambiente real.
3. [ ] Painel com a idade do pedido `PENDING` mais antigo.
