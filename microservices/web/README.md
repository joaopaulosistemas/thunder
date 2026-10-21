# Web: shell + microfrontends (Module Federation)

Angular 20 com **Native Federation** (`@angular-architects/native-federation`): a mesma ideia do
Module Federation (o host carrega remotos em tempo de execução, dependências compartilhadas como
singleton), só que sobre esbuild. O plugin de Module Federation do webpack não se aplica ao builder atual do Angular.

| App | Porta dev | Papel | Expõe |
|---|---|---|---|
| `shell` | 4200 | host: login (Keycloak), menu, roteamento, interceptor de token | nada |
| `mfe-catalog` | 4201 | catálogo (catalog-service) | `./routes` |
| `mfe-orders` | 4202 | pedidos (order-service) | `./routes` |
| `mfe-notifications` | 4203 | notificações (notification-service) | `./routes` |

- O shell descobre os remotos em `public/federation.manifest.json` (dev) ou `federation.manifest.prod.json` (a imagem Docker o instala como `federation.manifest.json`). Trocar o endereço de um remoto não exige recompilar o shell.
- Cada remoto é uma imagem própria (`Dockerfile`, `--build-arg APP=...`) e pode ser publicado sozinho.
- Os remotos usam o `HttpClient` compartilhado do shell, então herdam o `Authorization: Bearer`.
- Os remotos conversam por um evento do navegador (`shop:order-placed`), sem importar código uns dos outros.
- Se um remoto cai, o shell mostra "Módulo indisponível" e o resto segue.

## Comandos
```bash
npm ci
npm run start:all        # os 4 em modo dev (o shell exige Keycloak em :8180)
npm run build:all
npm run serve:dist       # serve os builds com CORS (portas 4200-4203)
npm i --no-save playwright && CHROMIUM_PATH=/caminho/chrome npm run test:federation
```
Rodar um remoto isolado (`ng serve mfe-orders`) não tem login: as chamadas `/api` voltam 401.
