# ADR 0005: Microsserviços com Kafka e microfrontends com Module Federation

Status: proposto (convive com o monólito modular das ADRs 0001-0004; escolha uma das duas para produção)

## Contexto
O monólito modular (ADR 0001) atende bem uma equipe só. Quando equipes diferentes precisarem publicar catálogo, pedidos e notificações em ritmos próprios, e escalar cada parte separadamente, as fronteiras que o ArchUnit já protege viram serviços.

## Decisão
**Backend** (`microservices/services`): três serviços Spring Boot, cada um com seu banco (database per service) e sua imagem.
- `catalog-service`: produtos e estoque. `order-service`: pedidos e saga. `notification-service`: avisos ao cliente.
- Comunicação **só por eventos Kafka** (contratos em `shop-contracts`). Nenhuma chamada síncrona entre serviços.
- Saga coreografada: `orders.created` → catálogo reserva → `stock.reserved` ou `stock.rejected` → pedido confirmado ou recusado → `orders.confirmed` ou `orders.rejected` → notificação.
- **Outbox transacional** em todo produtor e **inbox idempotente** em todo consumidor (`shop-common`): resolve a limitação do ADR 0002 (evento perdido se o Kafka cair) e a entrega "ao menos uma vez".
- O `order-service` guarda uma cópia local dos preços (`product_snapshot`), alimentada por `catalog.product-changed`, para aceitar pedidos mesmo com o catálogo fora do ar.
- Mensagens que falham 4 vezes vão para `<tópico>.DLT`.
- Persistência com Hibernate: entidades `Category` e `Product` com `@Version` (bloqueio otimista no CRUD), exclusão lógica de produto e `JOIN FETCH` na listagem. Ver `microservices/README.md`.

**Frontend** (`microservices/web`): Angular 20 com Native Federation. Um `shell` (login, menu, token) carrega em tempo de execução `mfe-catalog`, `mfe-orders` e `mfe-notifications`, cada um publicado de forma independente.

## Alternativas
- Manter o monólito: menos peças, uma transação. Melhor enquanto uma equipe só entrega tudo.
- Orquestração central da saga (Temporal, Camunda): fluxo visível num lugar só, mais uma plataforma para operar.
- Module Federation do webpack: o Angular moderno usa esbuild; Native Federation mantém o mesmo modelo (host, remotos, singletons) sobre ele.

## Consequências
- Consistência eventual: o pedido nasce `PENDING` (HTTP 202) e é confirmado em instantes; a tela atualiza sozinha.
- Mais operação: 3 bancos, 3 deploys, Kafka como dependência crítica, rastreamento distribuído indispensável (OpenTelemetry já incluído).
- Os eventos de produto usam um único tópico (`catalog.product-changed`, chave = id), então a ordem por produto é garantida. A saga tem compensações: timeout de pedido pendente, cancelamento e liberação idempotente de estoque.
- Mudar um evento exige compatibilidade: adicione campos, não renomeie nem remova.
- Cada serviço valida o JWT por conta própria (zero confiança na rede interna).
- Não incluído: canário por serviço (hoje `rolling update`), testes de contrato entre serviços (Pact), orquestrador de saga, compensação além de "recusar o pedido".
