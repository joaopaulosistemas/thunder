# Code Review: microsserviços (`microservices/services`)

Feito com a skill `engineering:code-review` em 2026-10-20. **Feita por leitura do código. Depois, o CI do GitHub compilou e executou tudo (commit b62b144, verde):** ver `docs/DIVIDA-TECNICA.md`.
Cobri: segurança (`SecurityConfig`, controllers), outbox/inbox, saga, reaper, catálogo (busca, cache, estoque), tratamento de erros.
Não cobri linha a linha: `notification-service`, mapeamentos JPA de `Product`/`Category`, o gateway e o módulo `order-workflow`.

## Resumo
Estrutura sólida (outbox/inbox, bloqueio otimista, validação de entrada, autorização por papel em cada serviço). A revisão achou
1 problema alto, 3 médios e 4 baixos. Os itens 1 a 5 e 7 foram corrigidos; nenhuma correção foi testada.

## Problemas
| # | Arquivo | Problema | Severidade | Estado |
|---|---|---|---|---|
| 1 | `shop-common/.../OutboxPublisher` + `OutboxRepository.lockPending` | Com 2+ réplicas, `skip locked` faz cada uma pegar um lote; eventos da MESMA chave (ex.: duas edições do mesmo produto) podem chegar ao Kafka fora de ordem. Isso anulava a garantia de ordem do tópico `catalog.product-changed`. | 🔴 Alto | Corrigido: lock de aviso `pg_try_advisory_xact_lock`, só uma réplica publica por vez; teste novo |
| 2 | `order-service` (sem handler) | Cancelar ao mesmo tempo em que a saga confirma gera `ObjectOptimisticLockingFailureException` e HTTP 500. | 🟠 Médio | Corrigido: `ApiErrors` devolve 409 |
| 3 | `outbox_event`, `processed_event` | Nenhuma retenção: as duas tabelas crescem para sempre. | 🟠 Médio | Corrigido: `OutboxCleaner` diário (outbox 3 dias, inbox 14 dias, configuráveis) |
| 4 | `CatalogService.search` | Parâmetro `q` sem limite de tamanho. | 🟡 Baixo | Corrigido: limitado a 100 caracteres |
| 5 | `ProductSpecs` | `LIKE '%q%'` não usa índice comum; com catálogo grande vira varredura. | 🟡 Baixo | Corrigido: migração `V5__name_trigram_index.sql` (GIN `pg_trgm`) |
| 6 | `SecurityConfig` | `/actuator/prometheus` e `/v3/api-docs` abertos sem token; a proteção é a NetworkPolicy e o gateway não rotear esses caminhos. | 🟡 Baixo | Aberto: ideal é porta de gerenciamento separada (`management.server.port`) |
| 7 | `OrderSaga.onProductChanged` | Sem versão no evento: reprocessar da DLT um upsert antigo pode sobrescrever o preço mais novo. | 🟡 Baixo | Corrigido: `ProductChanged.version`; o order-service ignora UPSERTED mais velho (`V5__snapshot_catalog_version.sql`) |
| 8 | `ProductCache.get` | Leitura concorrente a uma atualização pode regravar valor velho (limitado pelo TTL de 60 s). | 🟡 Baixo | Aberto (já no registro de dívida) |

## O que está bom
- Autorização em duas camadas (gateway e cada serviço), pedido de outro cliente responde 404 (não revela existência).
- Validação de entrada com limites (`@Min/@Max/@Size`) e lista fixa de campos de ordenação (sem injeção por `sort`).
- LIKE com escape de `%`, `_` e `\`; senhas e segredos fora do código (compose usa variáveis, k8s usa Secret/External Secrets).
- Escrita de evento e dado na mesma transação (`Outbox` exige transação via `MANDATORY`), inbox idempotente, estoque devolvido uma única vez.
- Falha do Redis não derruba leitura.

## Veredito
**Pedir mudanças → agora "precisa de discussão"**: as correções acima precisam passar por `mvn verify` antes de aprovar. O maior risco que sobra não é
um item da tabela, e sim a falta de qualquer execução: o bug do reaper (transação ignorada) só apareceu por leitura, e provavelmente há outros.
