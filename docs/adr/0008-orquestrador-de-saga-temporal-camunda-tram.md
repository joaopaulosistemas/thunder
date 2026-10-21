# ADR 0008: Orquestrador para a saga de pedidos (Temporal, Camunda ou Tram)

**Status:** Proposto (não adotar agora; ver gatilhos)
**Data:** 2026-10-20
**Decisores:** donos de order-service e catalog-service, plataforma

## Contexto
O ADR 0006 manteve a saga coreografada. Esta decisão responde: se e quando trocar por um orquestrador, e qual. Critérios do projeto:
Java 21 e Spring Boot, equipe pequena, Kafka e Postgres já operados, nenhum serviço novo de alta criticidade sem necessidade,
comunicação hoje só por eventos (ADR 0005).

## Gatilhos para migrar (qualquer um)
- A saga passar de ~5 passos ou ganhar compensações que dependam umas das outras.
- Aparecer necessidade de ver o estado de cada pedido num painel único ou auditar o histórico do fluxo.
- O código de timeout/retentativa/compensação virar fonte recorrente de bugs.

## Opções consideradas

### Opção A: manter a coreografia (hoje)
| Dimensão | Avaliação |
|---|---|
| Complexidade | Média, espalhada em 3 serviços |
| Custo operacional | Nenhum a mais |
| Visibilidade do fluxo | Baixa (logs e traces) |
| Familiaridade da equipe | Alta |

### Opção B: Temporal
| Dimensão | Avaliação |
|---|---|
| Complexidade | Média: modelo de workflow determinístico |
| Custo operacional | Alto se auto-hospedado (servidor + banco), ou pago na nuvem |
| Visibilidade do fluxo | Alta (histórico e UI) |
| Familiaridade da equipe | Baixa |

**Prós:** fluxo em Java comum; retentativas, timeouts, sinais e `Saga` de compensação nativos; testes com relógio simulado.
**Contras:** componente crítico novo; as etapas viram chamadas (activities), o que quebra a regra "só eventos" ou exige request/reply no Kafka.

### Opção C: Camunda 8 (Zeebe, BPMN)
| Dimensão | Avaliação |
|---|---|
| Complexidade | Média a alta |
| Custo operacional | Alto (cluster Zeebe); licença a conferir no uso em produção |
| Visibilidade do fluxo | Muito alta (diagrama) |
| Familiaridade da equipe | Baixa |

**Prós:** negócio enxerga e altera o processo. **Contras:** BPMN para um fluxo de 3 passos é pesado; a lógica fica dividida entre diagrama e código.

### Opção D: Eventuate Tram Sagas (biblioteca)
| Dimensão | Avaliação |
|---|---|
| Complexidade | Média |
| Custo operacional | Baixo: roda dentro dos serviços, sobre o banco e a mensageria |
| Visibilidade do fluxo | Média |
| Familiaridade da equipe | Baixa |

**Prós:** sem servidor novo; mantém mensagens. **Contras:** comunidade menor; integra com o outbox próprio do Tram, o que duplicaria o nosso.

## Análise de trade-offs
Temporal é a melhor opção SE migrarmos: resolve exatamente o que mais escrevemos à mão (timeout, compensação, retentativa) e o código
fica legível. O preço é operar um componente crítico e abrir mão do "só eventos" no fluxo de pedido. Camunda só vence se o negócio
precisar desenhar processos. Tram é o meio-termo, com ecossistema menor.

## Decisão
Não migrar agora. Fechar antes a dívida de testes de integração (DIVIDA-TECNICA.md, itens 1 e 3). Se um gatilho ocorrer, adotar
**Temporal** para o fluxo de pedido, mantendo Kafka para eventos de produto e notificação. O protótipo em
`microservices/services/order-workflow` mostra a forma, sem estar ligado ao build.

## Consequências
- Mais fácil (se migrar): estado do pedido num lugar só; compensações declarativas; testes com tempo simulado.
- Mais difícil: operar o servidor Temporal; as activities precisam ser idempotentes; versionar workflows em execução.
- Revisitar: custo de Temporal Cloud versus auto-hospedado; como o catálogo recebe comandos (HTTP ou request/reply Kafka).

## Ações
1. [ ] Rodar `mvn verify` e criar testes de integração (pré-requisito).
2. [ ] Compilar e testar o protótipo `order-workflow` com o Maven liberado.
3. [ ] Medir: quantos pedidos/dia e quantas compensações por semana, para confirmar o gatilho.
