# ADR 0003: Autenticação com Keycloak (OIDC) e JWT no front e no back

Status: aceito

## Contexto
A API estava aberta. Precisamos de login, identidade do usuário nos pedidos e controle por papel (cliente x admin), sem guardar senhas na aplicação.

## Decisão
1. **Keycloak** como provedor de identidade (realm `shop`, importado de `infra/keycloak/shop-realm.json`).
2. **Frontend** (client público `shop-frontend`): Authorization Code + **PKCE (S256)** via `keycloak-js`. O access token fica só em memória e é renovado com o refresh token. Um `HttpInterceptor` anexa `Authorization: Bearer` nas chamadas `/api`.
3. **Backend**: Spring Security como *resource server* stateless. Valida assinatura (JWKS), `iss`, expiração e `aud` (`shop-api`). Roles de realm (`realm_access.roles`) viram `ROLE_*`; o nome do usuário é o `preferred_username`.
4. **Regras**: `/api/**` exige `user` ou `admin`; `/api/admin/**` exige `admin`. O cliente do pedido vem do token, nunca do corpo. Usuário comum só vê os próprios pedidos e notificações.
5. `KC_HOSTNAME` fixa o `iss` em `http://localhost:8180/realms/shop`; o backend busca as chaves pela rede interna (`http://keycloak:8080`).

## Alternativas
- Sessão/cookie no backend: simples, mas acopla o front ao backend e complica o SSO.
- Fluxo implícito ou senha direta (ROPC): descartados, são desaconselhados pelo OAuth 2.1.
- Guardar o token em `localStorage`: sobrevive ao reload, mas fica exposto a XSS. Preferimos memória; o reload refaz o SSO de forma transparente.

## Consequências
- O token vale até expirar (5 min): revogar um usuário só tem efeito total após a expiração.
- `/actuator/prometheus` e `/actuator/health` ficam abertos para probes e scraping; em produção restrinja por rede (NetworkPolicy) ou porta de gestão separada.
- Keycloak em modo `start-dev` (H2, HTTP) é só para o protótipo. Produção: `start --optimized`, banco próprio, HTTPS, senhas fora do repositório e usuários/segredos reais.
- Os usuários `alice/alice` e `bob/bob` e a conta `admin/admin` do Keycloak são de demonstração.
