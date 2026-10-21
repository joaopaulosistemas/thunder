# SLO e orçamento de erro

| SLI | SLO | Orçamento de erro |
|---|---|---|
| Requisições `/api` sem resposta 5xx | 99,9% em 30 dias | 0,1% (~43 min de indisponibilidade equivalente por mês) |
| Latência p95 de `/api` | abaixo de 500 ms | acompanhar no Grafana |

Alertas (arquivo `observability/rules/slo.yml`):
- **Queima rápida:** 14,4x o ritmo permitido em 5 min e 1 h → página (reverter o último canário).
- **Queima lenta:** 6x o ritmo permitido em 30 min e 6 h → ticket (desacelerar releases).

Política: com orçamento sobrando, lançamos com o canário normal. Com o orçamento esgotado, só entram correções de confiabilidade até a janela se recuperar.

Sobre os números: as metas acima são sugestões de partida, não valores medidos; ajuste depois de observar o tráfego real.
