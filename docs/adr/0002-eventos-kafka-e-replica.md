# ADR 0002: Eventos via Kafka e réplica de leitura

Status: aceito

## Contexto
Queremos que módulos reajam a fatos de negócio (pedido criado) sem acoplamento direto, e aliviar o banco primário nas leituras.

## Decisão
1. `orders` publica `OrderCreatedEvent` (contrato em `orders.api`). Um relay só envia ao Kafka (`orders.created`) **depois do commit**. O módulo `notifications` consome o JSON do tópico e não conhece classes de `orders` (verificado por ArchUnit), então pode virar serviço separado.
2. Transações `readOnly=true` vão para uma réplica PostgreSQL (streaming replication); o resto vai ao primário.

## Alternativas
- Chamada síncrona entre módulos: mais simples, mas acopla e propaga falhas.
- Outbox transacional: mais robusto (não perde evento se o processo cair entre o commit e o envio), mas mais código. É o próximo passo recomendado para produção.
- Sem réplica: mais simples, mas o primário atende toda a leitura.

## Consequências
- Entrega ao menos uma vez: consumidores devem ser idempotentes.
- A réplica é eventualmente consistente: uma leitura logo após a escrita pode vir defasada; fluxos que precisam ler o próprio dado devem usar transação de escrita.
- Se o Kafka estiver fora, o pedido é salvo mas o evento se perde (limitação aceita neste protótipo; ver outbox).
