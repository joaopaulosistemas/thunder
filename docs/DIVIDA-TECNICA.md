# Registro de dívida técnica

Gerado com a skill `engineering:tech-debt` em 2026-10-20. Prioridade = (Impacto + Risco) × (6 − Esforço). Notas de 1 a 5 são estimativas minhas.

| # | Item | Tipo | Impacto | Risco | Esforço | Prioridade | Correção |
|---|---|---|---|---|---|---|---|
| 1 | Nenhum Java foi compilado nem testado; ~47 métodos de teste nunca rodaram | Teste | 5 | 5 | 1 | 50 | Rodar `mvn verify` em `microservices/services` (e corrigir). Achei um bug de transação só lendo o código; deve haver mais. |
| 2 | Métrica de lag do Kafka e springdoc 2.8.6 não confirmados | Dependência | 3 | 3 | 1 | 30 | Conferir no primeiro ambiente real e ajustar regra/versão. |
| 3 | Microsserviços sem teste de integração (Postgres e Kafka reais) | Teste | 5 | 4 | 3 | 27 | ESCRITOS (`OrderFlowIT`, `CatalogFlowIT`), falta executar. Cobrem saga, timeout, cancelamento, outbox e busca. |
| 4 | Keycloak da VM em `start-dev`, sem alta disponibilidade | Infra | 3 | 5 | 3 | 24 | Modo `start` com banco próprio e proxy configurado. |
| 5 | Contratos por arquivo JSON, sem Pact nem checagem de drift do OpenAPI | Teste | 3 | 3 | 3 | 18 | Gerar o OpenAPI no CI e comparar com o arquivo versionado. |
| 6 | Dois sistemas no mesmo repositório (monólito e microsserviços) com nomes e namespaces iguais | Arquitetura | 4 | 4 | 4 | 16 | Escolher um por cluster (ADR 0005 pede isso) e arquivar o outro, ou separar os repositórios. |
| 7 | Cache de produto: leitura concorrente pode regravar valor velho (limitado pelo TTL) | Código | 2 | 2 | 2 | 16 | Versionar a entrada (campo `version`) e só gravar se for mais nova. |
| 8 | Documentação longa e repetida (`PASSO-A-PASSO`, READMEs, HTML) | Documentação | 3 | 2 | 3 | 15 | Uma fonte por assunto; gerar o resto ou apontar para ela. |
| 9 | TLS interno Kafka/Postgres depende de servidor configurado fora do projeto | Infra | 3 | 4 | 4 | 14 | Documentar e automatizar a emissão (cert-manager) dos certificados dos brokers. |
| 10 | Sem réplica de leitura do Postgres | Arquitetura | 3 | 2 | 4 | 10 | Só fazer se a leitura do catálogo virar gargalo; Redis já alivia. |
| 11 | Constante `OrderSaga.PRODUCT = "orders.product-upserted"` com nome antigo | Código | 1 | 1 | 1 | 10 | Renomear para `orders.product-changed` (não é mais usada como chave de inbox). |

## Plano em fases (junto com o trabalho de funcionalidades)

**Fase 1 (esta semana):** itens 1 e 2 (`mvn verify`; conferir métrica e versão do springdoc). A renomeação da constante `PRODUCT` já foi feita.
**Fase 2 (próximo mês):** testes de integração com Testcontainers; checagem de drift do OpenAPI no CI.
**Fase 3 (trimestre):** decidir monólito ou microsserviços por cluster; Keycloak em modo produção; TLS interno automatizado.
**Só se doer:** réplica de leitura do Postgres, Pact, Argo Rollouts (ADR 0007), orquestrador Temporal (ADR 0008, protótipo em `order-workflow`).

## Achados da revisão de código (ver `docs/CODE-REVIEW.md`)
Feitos: índice `pg_trgm` para a busca e versão no evento `ProductChanged`. Em aberto: porta de gerenciamento separada para `/actuator` (mexe em probes, Prometheus e NetworkPolicy; precisa de ambiente para testar).
