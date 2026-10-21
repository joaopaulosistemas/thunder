# ADR 0001: Monolito modular em vez de microsservicos

Status: aceito

## Contexto
Equipe pequena, dominio ainda em descoberta. Microsservicos desde o dia 1 trazem custo de rede, deploy, observabilidade distribuida e consistencia de dados.

## Decisao
Um unico deploy Spring Boot com modulos por dominio (`catalog`, `orders`). Cada modulo expoe apenas um pacote `api`; a implementacao fica em `internal`. A regra e verificada por ArchUnit no CI.

## Alternativas
1. Microsservicos desde o inicio: isolamento maximo, mas custo operacional alto e fronteiras ainda incertas.
2. Monolito sem fronteiras: mais rapido hoje, mas vira "big ball of mud".

## Consequencias
- Extrair um modulo vira servico quando houver necessidade real de escala ou deploy independente; o contrato `CatalogApi` vira uma API HTTP/gRPC sem mudar os chamadores.
- Um unico banco compartilhado exige disciplina: cada modulo acessa apenas as proprias tabelas.
