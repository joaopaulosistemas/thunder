# ADR 0004: API Gateway dedicado (Spring Cloud Gateway)

Status: aceito

## Contexto
O "gateway" era só o nginx do frontend (limite por IP) e o Ingress. Faltava uma borda que conheça a identidade: validar o token, autorizar por papel e limitar por usuário, antes de a requisição tocar o backend.

## Decisão
Módulo `gateway/` (Spring Cloud Gateway, WebFlux) como única entrada da API:
- valida o JWT (assinatura, emissor, audiência) e o papel (`/api/admin/**` = admin; `/api/**` = user ou admin; o resto negado);
- limite por usuário com contador no Redis (vale para todas as réplicas), 20 req/s e rajada 40, configuráveis;
- retry só de GET em 502/503; POST nunca é repetido; timeouts de conexão (1s) e resposta (10s);
- injeta/propaga `X-Request-Id` e o trace (OpenTelemetry); cabeçalhos de segurança padrão;
- o backend deixa de ser publicado no host e no Kubernetes só é acessível pelo gateway.
O backend continua validando o token (defesa em profundidade).

## Alternativas
- Kong/Envoy/APISIX: mais recursos e fora da JVM, porém outra tecnologia para a equipe operar; o time já domina Spring.
- Manter só nginx/Ingress: sem consciência de identidade.
- Gateway gerenciado da nuvem: bom em produção, mas não roda no `docker compose`.

## Consequências
- Um salto de rede a mais (latência pequena) e um serviço a mais para operar e escalar.
- O canário passa a desviar tráfego para `gateway-canary`, que fala com `backend-canary`.
- Se o Redis cair, o limitador do Spring deixa passar (fail-open): a proteção fica só no limite por IP do nginx.
- Regras de papel existem em dois lugares (gateway e backend); manter sincronizadas.
