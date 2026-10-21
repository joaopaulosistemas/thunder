# Passo a passo: executar toda a stack

Guia em ordem cronológica, do zero até produção. Cada etapa traz o **conceito** (por que existe), o **comando**, o
**resultado esperado** e as **variáveis** que você pode trocar. Os valores de versão e de variável abaixo foram
copiados dos arquivos do projeto.

> **Escolha uma arquitetura por vez.** O repositório tem duas versões do mesmo produto:
> **A. Monólito modular** (`backend/` + `gateway/` + `frontend/`) e
> **B. Microsserviços + microfrontends** (`microservices/`).
> Ambas usam as portas 3000, 9090 e 8180/8443; não suba as duas juntas no mesmo computador nem no mesmo namespace do cluster.

## Índice

0. Mapa mental (conceitos)
1. Pré-requisitos e versões
2. Obter o código
3. Parametrização (todas as variáveis)
4. Caminho A: monólito no Docker Compose
5. Caminho B: microsserviços no Docker Compose (HTTPS)
6. Desenvolver sem empacotar tudo (hot reload)
7. Testar e checar qualidade
8. Kubernetes (elástico, com HA)
9. CI/CD e release
10. Operar: observar, escalar, derrubar
11. Problemas comuns
12. O que está verificado e o que não está

---

## 0. Mapa mental (conceitos)

| Peça | Papel | Por que existe |
|---|---|---|
| **Keycloak** | Servidor de identidade (OIDC) | Faz o login e emite o **JWT**. Nenhum serviço guarda senha. |
| **Angular (shell + microfrontends)** | Interface | O shell faz login (Authorization Code + PKCE) e carrega 3 telas independentes em tempo de execução (Native Federation, equivalente ao Module Federation). |
| **API Gateway** (Spring Cloud Gateway) | Única porta de entrada da API | Valida JWT e papéis, limita requisições por usuário (Redis), roteia para o serviço certo. |
| **catalog / order / notification-service** | Serviços de negócio | Cada um com **banco próprio** (PostgreSQL), validando o JWT de novo (zero confiança na rede). |
| **Kafka** | Mensageria | Os serviços conversam por eventos (saga do pedido), sem se chamar diretamente. |
| **Outbox / Inbox** | Padrões de confiabilidade | Outbox grava o evento na mesma transação do dado; Inbox ignora evento repetido. |
| **Hibernate (JPA)** | Persistência | Entidades com `@Version` (bloqueio otimista), exclusão lógica e `JOIN FETCH`. |
| **Prometheus, Alertmanager, Tempo, Loki, Grafana** | Observabilidade | Métricas, alertas de SLO, traces e logs ligados entre si. |
| **Kubernetes** | Execução elástica | HPA liga e desliga cópias, PDB garante que sempre haja cópia no ar, NetworkPolicy fecha a rede. |
| **TLS / cert-manager** | HTTPS | Compose: CA local. Kubernetes: Let's Encrypt com renovação automática. |

Fluxo de um pedido (microsserviços): `POST /api/orders` → order-service grava o pedido `PENDING` e o evento
`orders.created` (outbox) → catalog-service reserva estoque e emite `stock.reserved` ou `stock.rejected` →
order-service marca `CONFIRMED` ou `REJECTED` → notification-service avisa o cliente.

---

## 1. Pré-requisitos e versões

### 1.1 Para rodar localmente (Docker Compose)

| Ferramenta | Versão usada no projeto | Para quê |
|---|---|---|
| Docker Engine + Compose v2 | qualquer recente com `docker compose` (v2) | sobe a stack |
| OpenSSL | qualquer 1.1+ ou 3.x | gera o certificado local (caminho B) |
| 4 CPUs e 8 GB de RAM livres | recomendado | a stack inteira tem ~20 contêineres |

### 1.2 Para desenvolver sem empacotar tudo

| Ferramenta | Versão | Onde está fixada |
|---|---|---|
| JDK | **21** (testado com Temurin 21) | `<java.version>21` nos `pom.xml` |
| Maven | **3.9** | imagem `maven:3.9-eclipse-temurin-21` nos Dockerfiles |
| Spring Boot | **3.4.5** | `pom.xml` (pai) |
| Spring Cloud | **2024.0.1** | `gateway/pom.xml` |
| Node.js | **22** | CI e Dockerfile do web (`node:22-alpine`) |
| Angular | **^20.3** | `package.json` |
| Native Federation | **^20.3.1** (`@angular-architects/native-federation`) | `microservices/web/package.json` |
| TypeScript | **~5.9.2** | `package.json` |

### 1.3 Imagens de infraestrutura (fixadas no compose)

| Componente | Imagem |
|---|---|
| PostgreSQL | `postgres:16-alpine` |
| Redis | `redis:7-alpine` |
| Kafka (modo KRaft, sem ZooKeeper) | `apache/kafka:3.9.0` |
| Keycloak | `quay.io/keycloak/keycloak:26.0.7` |
| Nginx (borda TLS e microfrontends) | `nginx:1.27-alpine` |
| Prometheus / Alertmanager | `prom/prometheus:v2.55.1` / `prom/alertmanager:v0.27.0` |
| Tempo / Loki / Promtail | `grafana/tempo:2.6.1` / `grafana/loki:3.2.1` / `grafana/promtail:3.2.1` |
| Grafana | `grafana/grafana:11.3.1` |
| Receptor de alertas (teste) | `mendhak/http-https-echo:35` |
| SonarQube (só no monólito) | `sonarqube:community` |

### 1.4 Para Kubernetes (etapa 8)

`kubectl` e `kustomize` (ou `kubectl apply -k`), um cluster com **CNI que aplique NetworkPolicy** (Calico, Cilium,
Antrea), **metrics-server** (o HPA precisa), **ingress-nginx**, **cert-manager** (APIs `cert-manager.io/v1`),
opcional **Prometheus Operator** (`monitoring.coreos.com/v1`), **Karpenter** ou Cluster Autoscaler, e **KEDA**.
Fixe a versão de cada complemento com `--version` ao instalar; o projeto só depende das APIs citadas.

Conferir o ambiente:

```bash
docker --version && docker compose version
java -version        # 21
mvn -version         # 3.9.x
node -v && npm -v    # 22.x
openssl version
```

---

## 2. Obter o código

```bash
tar xzf prototype.tar.gz
cd prototype
```

Estrutura que importa:

```
backend/            monólito modular (Spring Boot)
gateway/            API Gateway (serve monólito e microsserviços por perfil)
frontend/           Angular do monólito
microservices/
  services/         catalog-service, order-service, notification-service, shop-common, shop-contracts
  web/              shell + 3 microfrontends (Angular workspace)
  k8s/              base, overlays staging/production, cert-manager, observability
  infra/            postgres-init.sh, tls/ (edge.conf, gen-certs.sh), shell-config.https.json
infra/keycloak/     realm "shop" importado automaticamente
k8s/                manifestos do monólito
scripts/            deploy, canary, smoke, elasticidade, proteção da main
docs/               ADRs, SLO, este guia
```

---

## 3. Parametrização (todas as variáveis)

Conceito: **a mesma imagem roda em qualquer ambiente**; o que muda vem de variáveis de ambiente (12-factor). No
Compose elas estão no `docker-compose.yml`; no Kubernetes, em `ConfigMap` (`shop-config`) e `Secret`.

### 3.1 Serviços Spring (catalog, order, notification) — `shop-common.yml`

| Variável | Padrão | Significado |
|---|---|---|
| `DB_URL` | definido por serviço (ex. `jdbc:postgresql://postgres:5432/catalog`) | banco do serviço |
| `DB_USER` / `DB_PASSWORD` | `shop` / `shop` | credencial (em produção: `Secret`, uma por serviço) |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | endereço do Kafka |
| `KAFKA_TOPIC_REPLICAS` | `1` | réplicas dos tópicos (em produção use `3`) |
| `AUTH_ISSUER` | `http://localhost:8180/realms/shop` | emissor esperado no JWT (`iss`) |
| `AUTH_JWK_SET_URI` | `…/protocol/openid-connect/certs` | onde buscar as chaves públicas |
| `AUTH_AUDIENCE` | `shop-api` | audiência obrigatória no JWT |
| `TRACING_SAMPLING` | `1.0` (dev) / `0.1` (k8s) | fração de traces coletados |
| `OTEL_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | destino dos traces (Tempo) |
| `LOG_FORMAT` | `ecs` | formato JSON dos logs |

### 3.2 Gateway

| Variável | Padrão | Significado |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | vazio | `microservices` troca as rotas para os 3 serviços |
| `BACKEND_URL` | `http://localhost:8081` | destino no modo monólito |
| `CATALOG_URL` / `ORDERS_URL` / `NOTIFICATIONS_URL` | `http://<serviço>:8080` | destinos no modo microsserviços |
| `REDIS_HOST` | `localhost` | contadores do limite de requisições |
| `RATE_LIMIT_RPS` / `RATE_LIMIT_BURST` | `20` / `40` | requisições por segundo e rajada, por usuário |
| `AUTH_*`, `TRACING_SAMPLING`, `OTEL_TRACES_ENDPOINT` | iguais aos dos serviços | |

### 3.3 Infraestrutura

| Componente | Variáveis principais |
|---|---|
| PostgreSQL | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`; `infra/postgres-init.sh` cria os bancos `catalog`, `orders`, `notifications` |
| Keycloak | `KC_BOOTSTRAP_ADMIN_USERNAME/PASSWORD` (`admin`/`admin`, só dev), `KC_HOSTNAME` (URL pública, `https://localhost:8444`), `KC_PROXY_HEADERS=xforwarded` (o TLS termina na borda), `KC_HTTP_ENABLED`, `KC_HEALTH_ENABLED` |
| Kafka (KRaft) | `KAFKA_NODE_ID`, `KAFKA_PROCESS_ROLES`, `KAFKA_LISTENERS`, `KAFKA_ADVERTISED_LISTENERS`, fatores de replicação (1 em dev) |
| Grafana | `GF_AUTH_ANONYMOUS_ENABLED` (acesso sem login só em dev) |
| Alertmanager (receptor de teste) | `HTTP_PORT` |

### 3.4 Frontend

| Arquivo | Conteúdo |
|---|---|
| `config.json` (lido em runtime pelo shell) | `keycloakUrl`, `realm`, `clientId`. Compose: `infra/shell-config.https.json`. Kubernetes: `ConfigMap frontend-config` |
| `federation.manifest.json` (dev) | URLs dos remotos em `http://localhost:4201..4203` |
| `federation.manifest.prod.json` (vira `federation.manifest.json` na imagem) | caminhos `/mfe/catalog/`, `/mfe/orders/`, `/mfe/notifications/` no mesmo domínio |

> Conceito: o shell **não é recompilado** para trocar um microfrontend de versão. Ele lê o manifest em runtime, por
> isso `config.json` e manifests são servidos com `Cache-Control: no-store`.

### 3.5 Pontos de ajuste mais comuns

| Quero… | Mude |
|---|---|
| mais ou menos tráfego por usuário | `RATE_LIMIT_RPS` / `RATE_LIMIT_BURST` |
| menos custo de tracing | `TRACING_SAMPLING` |
| mais paralelismo de consumo Kafka | `PARTITIONS` em `KafkaConfig` **e** `maxReplicas` dos HPAs (devem ser iguais, hoje 6) |
| mais cópias | `minReplicas` / `maxReplicas` nos HPAs (`k8s/base/*.yaml`; overlays reduzem em staging) |
| outro domínio | `host` e `tls.hosts` do Ingress (patch no overlay), `AUTH_ISSUER`, `config.json` |

---

## 4. Caminho A: monólito no Docker Compose

Conceito: um único processo Spring com módulos isolados (ArchUnit impede um módulo de acessar o interior de outro),
atrás do gateway.

```bash
cd prototype
docker compose up -d --build --wait
docker compose ps
```

| O quê | Onde |
|---|---|
| Aplicação | http://localhost:4200 |
| API e Swagger | http://localhost:8080 |
| Keycloak | http://localhost:8180 |
| Prometheus / Alertmanager | http://localhost:9090 / http://localhost:9093 |
| Grafana | http://localhost:3000 |

Usuários do realm `shop`: **alice / alice** (papel `user`) e **bob / bob** (papéis `user` e `admin`).

Parar: `docker compose down` (apaga também os volumes com `-v`).

---

## 5. Caminho B: microsserviços e microfrontends (HTTPS)

### Passo 5.1 — Gerar o certificado digital local

Conceito: o navegador só confia em certificados emitidos por uma autoridade (CA) que ele conhece. Em desenvolvimento
criamos uma CA própria e um certificado de servidor com **SAN** (nomes válidos: `localhost`, `127.0.0.1`).

```bash
cd microservices
infra/tls/gen-certs.sh          # opcional: informe os dias de validade, ex. 730
```

Saída em `infra/tls/certs/`: `ca.crt`, `ca.key`, `server.crt`, `server.key`. Para o navegador confiar, **importe
`ca.crt` como autoridade raiz** (ou use `mkcert`). Sem isso o app funciona, mas com aviso de segurança.

Conferir:

```bash
openssl verify -CAfile infra/tls/certs/ca.crt infra/tls/certs/server.crt   # server.crt: OK
```

### Passo 5.2 — Subir tudo

```bash
docker compose -f docker-compose.yml up -d --build --wait
docker compose -f docker-compose.yml ps
```

Ordem em que as coisas ficam prontas (o Compose respeita `depends_on` e health checks):

1. `postgres` (cria os 3 bancos) → `redis` → `kafka`
2. `keycloak` (importa o realm `shop`)
3. `catalog-service`, `order-service`, `notification-service` (Flyway cria as tabelas e os tópicos Kafka são criados)
4. `gateway`
5. `mfe-catalog`, `mfe-orders`, `mfe-notifications`, `shell`
6. `edge` (a borda HTTPS) e a pilha de observabilidade

> A primeira execução baixa imagens e compila 3 serviços + 4 apps Angular: leva vários minutos.

| O quê | Onde |
|---|---|
| **Aplicação** | https://localhost:8443 |
| Keycloak (admin: `admin` / `admin`) | https://localhost:8444 |
| `http://localhost:8000` | redireciona (301) para HTTPS |
| Grafana / Prometheus | http://localhost:3000 / http://localhost:9090 |

Gateway, serviços e Keycloak **não publicam porta em HTTP**: tudo entra pela borda TLS.

### Passo 5.3 — Verificar a saúde

```bash
docker compose ps                                   # todos "healthy" ou "running"
docker compose logs -f order-service                # acompanhar um serviço
docker compose exec gateway curl -fsS http://localhost:8080/actuator/health/readiness
```

### Passo 5.4 — Entrar e usar (CRUD)

1. Abra https://localhost:8443. O shell redireciona para o Keycloak (login com **Authorization Code + PKCE**).
2. Entre como **bob / bob** (admin). Em **Catálogo** crie, edite e exclua produtos e categorias.
3. Entre como **alice / alice**: ela vê os mesmos dados, sem os botões de escrita.

O que cada ação faz no backend:

| Ação | Requisição | Efeito |
|---|---|---|
| Listar | `GET /api/products` | uma consulta com `JOIN FETCH` (sem N+1) |
| Criar | `POST /api/admin/products` | grava produto e evento `catalog.product-changed` tipo `UPSERTED` (outbox) |
| Editar | `PUT /api/admin/products/{id}` com `version` | `UPDATE … WHERE version=?`; versão velha devolve **409** |
| Excluir | `DELETE /api/admin/products/{id}` | exclusão lógica; emite `catalog.product-changed` tipo `REMOVED` |

Teste o bloqueio otimista: abra o mesmo produto em duas abas, salve na primeira e depois na segunda. A segunda deve
mostrar o aviso de conflito.

### Passo 5.5 — Fazer um pedido e ver a saga

1. Como alice, abra **Pedidos**, escolha um produto e faça o pedido. Ele aparece como `PENDING`.
2. Em segundos a tela atualiza sozinha para `CONFIRMED` (ou `REJECTED` se faltar estoque).
3. Em **Notificações** aparece o aviso.

Conceito: a resposta é **202** (aceito) porque o resultado vem de forma assíncrona pela saga. Para ver os eventos:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic orders.confirmed --from-beginning --timeout-ms 5000
```

Para ver um fluxo de falha, peça mais unidades do que o estoque: o pedido vira `REJECTED`.

### Passo 5.6 — Observar

- **Grafana** (http://localhost:3000, sem login em dev): painéis de métricas; do log abra o **trace** correspondente.
- **Prometheus** (http://localhost:9090): consulte `http_server_requests_seconds_count`.
- Alertas de SLO (disponibilidade 99,9% por taxa de consumo do orçamento de erro) em `observability/` e no
  Alertmanager.

### Passo 5.7 — Derrubar

```bash
docker compose -f docker-compose.yml down        # mantém dados
docker compose -f docker-compose.yml down -v     # apaga também bancos e volumes
```

---

## 6. Desenvolver sem empacotar tudo (hot reload)

Conceito: sobe só a infraestrutura em contêiner e roda o código no seu computador.

### 6.1 Infraestrutura mínima

```bash
cd microservices
docker compose up -d postgres redis kafka keycloak
```

O `keycloak` do Compose usa `KC_HOSTNAME=https://localhost:8444` (atrás da borda). Para desenvolvimento sem a borda,
suba também o `edge` (`docker compose up -d edge`) e use as mesmas URLs HTTPS, ou ajuste `AUTH_ISSUER`.

### 6.2 Backend (cada serviço na sua porta)

```bash
cd services
export AUTH_ISSUER=https://localhost:8444/realms/shop
export AUTH_JWK_SET_URI=http://localhost:8180/realms/shop/protocol/openid-connect/certs  # se publicar a 8180
export KAFKA_BOOTSTRAP=localhost:9092
mvn -B -pl catalog-service -am spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8081 --DB_URL=jdbc:postgresql://localhost:5432/catalog"
```

Repita para `order-service` (`DB_URL=…/orders`, porta 8082) e `notification-service` (`…/notifications`, porta 8083).
O Postgres do Compose não publica a porta 5432 por padrão; adicione `ports: ["5432:5432"]` ao serviço `postgres`
para este modo. Gateway: `cd ../../gateway && SPRING_PROFILES_ACTIVE=microservices CATALOG_URL=http://localhost:8081 ORDERS_URL=http://localhost:8082 NOTIFICATIONS_URL=http://localhost:8083 mvn spring-boot:run`.

### 6.3 Frontend

```bash
cd microservices/web
npm ci
npm run start:all        # shell em :4200, remotos em :4201, :4202, :4203
```

Em dev o shell lê `federation.manifest.json` apontando para `localhost:4201..4203`.

---

## 7. Testar e checar qualidade

| O quê | Comando | O que prova |
|---|---|---|
| Testes do monólito (JUnit, Testcontainers, ArchUnit) | `cd backend && mvn -B verify` | regras de negócio, limites entre módulos |
| Testes do gateway | `cd gateway && mvn -B verify` | roteamento, segurança |
| Testes dos microsserviços (22 métodos) | `cd microservices/services && mvn -B verify` | estoque, saga, outbox, CRUD, bloqueio otimista |
| Build dos 4 apps Angular | `cd microservices/web && npm ci && npm run build:all` | compila shell e remotos |
| Federação e CRUD no navegador | `npm run build:all && npm run test:federation` | 16 checagens em Chromium (precisa do Playwright: `npm i --no-save playwright`) |
| Frontend do monólito | `cd frontend && npm ci && npm test` | testes de componente |
| End-to-end completo | `docker compose up -d --build --wait` e `cd e2e && npm ci && npx playwright test` | fluxo real, `BASE_URL` padrão `http://localhost:4200` |
| Manifestos Kubernetes | `kustomize build microservices/k8s/overlays/production > /dev/null` | YAML válido nos overlays |
| Compose | `docker compose -f microservices/docker-compose.yml config -q` | arquivo válido |
| Workflows e scripts | `actionlint .github/workflows/*.yml` e `shellcheck scripts/*.sh` | |

---

## 8. Kubernetes (elástico, com HA)

Conceitos:

- **HPA** (`autoscaling/v2`): sobe rápido (carga) e desce devagar (evita oscilar). `minReplicas: 2`, `maxReplicas: 6`
  por app em produção; staging usa 1 a 2 ou 3.
- **PDB**: garante que sempre exista cópia no ar durante manutenção de nós.
- **topologySpread + preStop**: espalha as cópias entre zonas e encerra conexões com calma.
- **Rolling update** com `maxUnavailable: 0`: o pod novo precisa ficar pronto antes de o antigo sair.
- **NetworkPolicy**: nega entrada por padrão; só ingress → shell/gateway, shell → remotos, gateway → serviços,
  Prometheus → métricas.
- **Nós**: remover pods não reduz a conta; Karpenter (ou Cluster Autoscaler) remove máquinas vazias.

### Passo 8.1 — Preparar o cluster (uma vez)

```bash
# complementos (fixe versões com --version em produção)
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo add jetstack https://charts.jetstack.io
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install ingress-nginx ingress-nginx/ingress-nginx -n ingress-nginx --create-namespace
helm install cert-manager jetstack/cert-manager -n cert-manager --create-namespace --set crds.enabled=true
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack -n monitoring --create-namespace
# metrics-server (exigido pelo HPA): instale o do seu provedor, se ainda não existir
kubectl top nodes     # deve responder
```

### Passo 8.2 — Dependências de dados

O projeto **consome** estes serviços, não os instala:

| Dependência | Endereço esperado (em `shop-config`) |
|---|---|
| Kafka | `kafka.shop.svc.cluster.local:9092` (produção: MSK, Confluent ou operador Strimzi) |
| Redis | `redis.shop.svc.cluster.local` (produção: ElastiCache, Memorystore) |
| PostgreSQL | `catalog-db:5432`, `orders-db:5432`, `notifications-db:5432` (um por serviço) |
| Keycloak | emissor `https://auth.example.com/realms/shop` e JWKS interno `keycloak.auth.svc.cluster.local:8080` |
| Tempo | `tempo.observability.svc.cluster.local:4318` |

Ajuste esses valores no `ConfigMap` (`microservices/k8s/base/configmap.yaml`) ou por patch no overlay.

### Passo 8.3 — Segredos

```bash
kubectl create namespace shop
kubectl -n shop create secret generic db-catalog       --from-literal=username=catalog_user       --from-literal=password='<senha>'
kubectl -n shop create secret generic db-orders        --from-literal=username=orders_user        --from-literal=password='<senha>'
kubectl -n shop create secret generic db-notifications --from-literal=username=notifications_user --from-literal=password='<senha>'
```

Um segredo **por serviço**: o order-service não tem a credencial do banco do catálogo. `secret.example.yaml` é só
modelo (use External Secrets ou Sealed Secrets em produção).

### Passo 8.4 — Certificado digital (HTTPS)

```bash
# edite o e-mail em microservices/k8s/cert-manager/cluster-issuers.yaml antes
kubectl apply -f microservices/k8s/cert-manager/cluster-issuers.yaml
```

O Ingress já traz a anotação `cert-manager.io/cluster-issuer`. **Staging usa `letsencrypt-staging`** (certificado de
teste, não confiável pelo navegador) e **produção usa `letsencrypt-prod`**. O domínio precisa apontar para o
Ingress (DNS) para o desafio HTTP-01 funcionar. Troque `shop.example.com` por patch no overlay.

### Passo 8.5 — Aplicar

```bash
kubectl apply -k microservices/k8s/overlays/staging      # namespace shop-staging
kubectl -n shop-staging get pods,hpa,ingress
kubectl apply -f microservices/k8s/observability/podmonitor.yaml   # métricas
```

Produção: `kubectl apply -k microservices/k8s/overlays/production`.

### Passo 8.6 — Verificar

```bash
kubectl -n shop-staging rollout status deployment/gateway
kubectl -n shop-staging get certificate          # READY=True quando o certificado for emitido
scripts/smoke.sh shop-staging                    # health do gateway
```

### Passo 8.7 — Provar a elasticidade e a alta disponibilidade

```bash
scripts/elasticity-check.sh status   shop-staging   # estado atual de HPA e pods
scripts/elasticity-check.sh failover shop-staging   # derruba um pod; outro assume
scripts/elasticity-check.sh load     shop-staging   # gera carga: o HPA sobe cópias
scripts/elasticity-check.sh stop-load shop-staging  # para a carga; as cópias descem devagar
```

Opcional: `k8s/optional/karpenter-nodepool.yaml` (desliga máquinas vazias) e `k8s/optional/keda-backend.yaml` (escala
por requisições por segundo, no monólito). Detalhes em `docs/kubernetes-elasticidade.md`.

### Monólito em Kubernetes

```bash
kubectl apply -k k8s/overlays/staging
```

---

## 9. CI/CD e release

### Passo 9.1 — Pipeline de integração (`.github/workflows/ci.yml`)

Jobs: `backend`, `gateway`, `services`, `web`, `frontend`, `manifests` (4 overlays, shellcheck, actionlint, compose),
`e2e`. Roda em todo pull request.

### Passo 9.2 — Proteger a main (uma vez, precisa de `gh` autenticado como administrador)

```bash
scripts/protect-main.sh <dono>/<repositorio>
```

Exige PR, aprovação dos donos (CODEOWNERS), CI verde (7 checks) e histórico linear; proíbe push direto e force-push.

### Passo 9.3 — Segredos e ambientes do GitHub

| Item | Para quê |
|---|---|
| `KUBE_CONFIG_STAGING`, `KUBE_CONFIG` (kubeconfig em base64) | deploy em staging e produção |
| `PROM_URL` | consulta de SLO no canário |
| Environment `production` com revisores obrigatórios | aprovação manual antes de produção |

### Passo 9.4 — Release

- **Monólito** (`release.yml`, automático após o CI na main): publica 3 imagens em `ghcr.io`, sobe em staging com
  *smoke test*, pede aprovação e então faz **canário** do backend (5% → 25% → 50% → 100%) com rollback automático se
  a taxa de 5xx passar de `MAX_ERROR_RATIO` (padrão `0.01`). Parâmetros do canário: `STEPS`, `SOAK_SECONDS`
  (padrão 300), `MAX_ERROR_RATIO`, `ALLOW_NO_DATA`.
- **Microsserviços** (`release-microservices.yml`, **manual** via *Run workflow*): publica 8 imagens (uma por
  serviço e por microfrontend) com a mesma tag (o SHA), faz rolling update em staging, pede aprovação e repete em
  produção. É manual porque os dois releases usam os mesmos nomes e namespaces e se sobrescreveriam. Equivalente
  local: `OWNER=<dono> scripts/deploy-microservices.sh staging <tag>`.

---

## 10. Operar: observar, escalar, derrubar

| Tarefa | Como |
|---|---|
| Ver logs de um serviço (Compose) | `docker compose logs -f <serviço>` |
| Ver logs (Kubernetes) | `kubectl -n shop logs -f deploy/order-service` |
| Mensagens com falha | tópicos `<nome>.DLT` no Kafka (alerte e reprocesse depois de corrigir a causa) |
| Reiniciar um serviço | `docker compose restart order-service` / `kubectl -n shop rollout restart deploy/order-service` |
| Reverter um release | `kubectl -n shop rollout undo deploy/<nome>` |
| Ver certificado | `kubectl -n shop get certificate,secret shop-tls` |
| Limpar tudo (Compose) | `docker compose down -v` |
| Limpar tudo (k8s) | `kubectl delete -k microservices/k8s/overlays/staging` |

---

## 11. Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| Aviso de certificado no navegador | a CA local não foi importada | importe `infra/tls/certs/ca.crt` |
| `nginx` da borda não sobe, "no such file … server.crt" | certificado não gerado | rode `infra/tls/gen-certs.sh` antes do `up` |
| Login volta com "invalid redirect uri" | acessou por URL que o realm não lista | use `https://localhost:8443` (ou edite `redirectUris` em `infra/keycloak/shop-realm.json`) |
| API responde 401 | `iss` do token diferente de `AUTH_ISSUER` | `KC_HOSTNAME` e `AUTH_ISSUER` devem ser a mesma URL pública |
| API responde 403 em escrita | usuário sem papel `admin` | entre como `bob` |
| Salvar produto devolve 409 | alguém alterou antes (bloqueio otimista) | recarregue e refaça |
| Excluir categoria devolve 409 | ainda há produtos nela | mova ou exclua os produtos |
| Pedido fica `PENDING` | consumidor Kafka parado ou mensagem na DLT | `docker compose logs order-service catalog-service`; veja `*.DLT` |
| Portas 3000/9090 ocupadas | o outro caminho (A ou B) está no ar | `docker compose down` no outro |
| `kustomize build` ou pods sem NetworkPolicy efetiva | CNI sem suporte | use Calico, Cilium ou Antrea |
| Certificado do Ingress não emite | DNS não aponta para o Ingress, ou limite do Let's Encrypt | `kubectl describe certificate,order,challenge`; teste antes com o emissor staging |
| HPA mostra `<unknown>` | metrics-server ausente | instale e confira `kubectl top pods` |
| Réplicas extras sem trabalho (consumo Kafka) | réplicas > partições | mantenha `maxReplicas` = `PARTITIONS` (6) |

---

## 12. O que está verificado e o que não está

Seja transparente com a equipe antes de depender disto em produção.

**Verificado de fato** (executado neste ambiente):

- Build dos 4 apps Angular e **16 checagens em Chromium** (federação, Bearer em todas as chamadas, fallback quando um
  remoto cai, CRUD de produtos e categorias, conflito 409) contra uma API simulada.
- `kustomize build` dos 4 overlays, `docker compose config` dos 2 arquivos, `actionlint`, `shellcheck`.
- Geração e validação do certificado (`openssl verify`, SAN) e um servidor TLS de teste que o aceitou com a CA, o
  recusou sem ela e recusou TLS 1.1.
- O script `deploy-microservices.sh` (com `kubectl` simulado): as 8 imagens saem com a tag e o dono corretos.

**Não executado** (sem Maven Central, Docker nem cluster neste ambiente):

- Compilação e os **22 testes** Java; Hibernate e migrações Flyway em PostgreSQL real.
- Kafka (saga, outbox, DLT), Keycloak com login real, o nginx da borda no ar.
- Kubernetes: aplicação, HPA, canário, NetworkPolicy, emissão real pelo Let's Encrypt.

**Limites conhecidos:** tráfego interno do cluster (pod a pod, banco, Kafka) é HTTP; os microfrontends abertos
sozinhos não têm login; upsert atrasado pode recriar um produto removido no snapshot do pedido (tópicos distintos);
sem canário por serviço nos microsserviços.

**Recomendação de primeira execução:** rode `mvn -B verify` em uma máquina com acesso ao Maven Central (ou pelo CI)
antes do Passo 5.2. Se algo do Java não compilar, é o ponto mais provável de ajuste.


## 13. Novidades da última evolução (comandos)

| Item | Como usar |
|---|---|
| Busca e paginação | `GET /api/products?q=livro&page=0&size=10&sort=name,asc` |
| Cache Redis | `REDIS_HOST=redis`, `PRODUCT_CACHE_TTL=PT60S` (opcional; sem Redis lê do banco) |
| Timeout da saga | `ORDER_PENDING_TIMEOUT=PT2M`, `ORDER_REAPER_POLL_MS=30000` |
| Cancelar pedido | `POST /api/orders/{id}/cancel` (botão "Cancelar" na tela de pedidos) |
| OpenAPI | `/v3/api-docs` em cada serviço; Swagger UI com `SWAGGER_UI=true` |
| Testes de contrato | `cd microservices/services && mvn -pl shop-contracts verify` |
| Painel e alertas | Grafana "Shop - Servicos"; regras em `observability/rules/services.yml` |
| Canário por serviço | `scripts/canary-service.sh catalog ghcr.io/ORG/shop-catalog:SHA` (precisa de `PROM_URL`) |
| mTLS (Linkerd) | `linkerd install --crds \| kubectl apply -f -`, `linkerd install \| kubectl apply -f -`; reaplique o overlay (namespace com `linkerd.io/inject`) |
| Segredos externos | instale o External Secrets Operator, crie o `ClusterSecretStore` (`optional/external-secrets/cluster-secret-store.example.yaml`) e use `kubectl apply -k microservices/k8s/overlays/production-secure` |
| VM com domínio | `DOMAIN=loja.exemplo.com ACME_EMAIL=ops@exemplo.com microservices/infra/vm/render.sh` e `docker compose -f microservices/docker-compose.yml -f microservices/docker-compose.vm.yml --env-file microservices/infra/vm/generated/.env up -d` |
| E2E microsserviços | `microservices/infra/tls/gen-certs.sh`, suba o compose, `cd e2e && npm ci && npx playwright test -c playwright.ms.config.ts` |

Não implementado: roteamento para réplica de leitura do Postgres. Nada de Java foi compilado neste ambiente: rode `mvn verify` primeiro.

Testes de integração dos serviços (precisam de Docker): `cd microservices/services && mvn -B verify` (os `*IT` rodam depois dos `*Test`). A release dos microsserviços agora só publica se esse `verify` passar.
