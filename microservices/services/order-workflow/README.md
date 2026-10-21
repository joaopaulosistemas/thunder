# order-workflow (protótipo, não ligado ao build)

Mostra a saga do pedido (ADR 0006) como workflow do Temporal (ADR 0008). **Nunca foi compilado nem testado**: o Maven
Central estava bloqueado onde foi escrito, e a versão do SDK (`temporal.version`) precisa ser confirmada.

- `OrderWorkflowImpl`: reservar → confirmar; compensa (devolve estoque) se algo falhar; aceita o sinal `cancel`.
- `OrderActivities`: os passos com efeito externo; a implementação real (HTTP ou Kafka) não existe ainda.
- Teste com o servidor em memória do Temporal: `mvn test` dentro desta pasta.

Limites conhecidos: o sinal `cancel` só vale antes do `confirmOrder` (um cancelamento depois da confirmação seguiria o fluxo
atual do order-service); não há timeout de espera pela reserva além do da activity; nada disso foi ligado ao Kafka.
