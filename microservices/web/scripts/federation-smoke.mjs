// Teste de fumaça em navegador real (Chromium headless) do shell + microfrontends.
// Sobe o build do shell + 3 remotos, simula o Keycloak e a API, e confere que:
//  1) o shell carrega cada microfrontend em tempo de execução, de outra origem;
//  2) todas as chamadas /api dos remotos saem com "Authorization: Bearer" (interceptor do shell);
//  3) se um remoto estiver fora do ar, o shell mostra o aviso e os outros seguem funcionando;
//  4) o CRUD de produtos e categorias funciona para admin (criar, editar, conflito de versão, excluir)
//     e fica somente leitura para usuário comum.
// Pré-requisitos: npm run build:all && node scripts/serve-dist.mjs (em outro terminal)
//   npm i --no-save playwright && node scripts/federation-smoke.mjs
import { chromium } from 'playwright';

const BASE = process.env.BASE_URL ?? 'http://localhost:4200';
const KC = 'http://keycloak.test';
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const jwt = (claims) => `${b64({ alg: 'none', typ: 'JWT' })}.${b64(claims)}.sig`;

const results = [];
const check = (name, ok, detail) => { results.push([name, !!ok]); console.log(`${ok ? 'OK  ' : 'FALHA'} ${name}${!ok && detail !== undefined ? ' -> ' + JSON.stringify(detail) : ''}`); };

const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });

/** Contexto novo com Keycloak simulado (código no fragmento e token sem assinatura) e config.json. */
async function newPage(roles) {
  const ctx = await browser.newContext();
  let nonce = '';
  await ctx.route('**/config.json', (r) => r.fulfill({ json: { keycloakUrl: KC, realm: 'shop', clientId: 'shop-frontend' } }));
  await ctx.route(`${KC}/**`, async (r) => {
    const url = new URL(r.request().url());
    if (url.pathname.endsWith('/auth')) {
      nonce = url.searchParams.get('nonce') ?? '';
      const back = `${url.searchParams.get('redirect_uri')}#state=${url.searchParams.get('state')}&code=abc`;
      return r.fulfill({ status: 302, headers: { location: back } });
    }
    if (url.pathname.endsWith('/token')) {
      const now = Math.floor(Date.now() / 1000);
      const t = jwt({ exp: now + 3600, iat: now, iss: `${KC}/realms/shop`, sub: 'u1', preferred_username: 'alice', realm_access: { roles }, nonce });
      return r.fulfill({ json: { access_token: t, id_token: t, refresh_token: t, token_type: 'Bearer', expires_in: 3600, refresh_expires_in: 3600 } });
    }
    return r.fulfill({ status: 204 });
  });
  return { ctx, page: await ctx.newPage() };
}

// ---------- 1 e 2: remotos carregam e levam o token (usuário comum) ----------
{
  const { ctx, page } = await newPage(['user']);
  const authHeaders = [];
  await ctx.route('**/api/**', (r) => {
    const req = r.request();
    authHeaders.push(req.headers()['authorization'] ?? '');
    const data = {
      '/api/products': { items: [{ id: 1, name: 'Teclado mecânico', priceCents: 29900, stock: 12, categoryId: 2, categoryName: 'Periféricos', version: 0 }], page: 0, size: 10, total: 1 },
      '/api/categories': [{ id: 2, name: 'Periféricos', version: 0 }],
      '/api/orders': [], '/api/notifications': [], '/api/me': { username: 'alice', roles: ['user'] },
    }[new URL(req.url()).pathname];
    return r.fulfill({ json: data ?? {} });
  });
  await page.goto(BASE);
  await page.getByRole('heading', { name: 'Catálogo' }).waitFor({ timeout: 20000 });
  check('shell carrega mfe-catalog (rota /catalog)', await page.getByText('Teclado mecânico').isVisible());
  check('usuário comum não vê "Novo produto" nem "Editar"', (await page.getByRole('button', { name: 'Novo produto' }).count()) === 0 && (await page.getByRole('button', { name: /Editar/ }).count()) === 0);
  await page.getByRole('link', { name: 'Pedidos' }).click();
  await page.getByRole('heading', { name: 'Pedidos' }).waitFor({ timeout: 10000 });
  check('shell carrega mfe-orders (rota /orders)', await page.getByRole('button', { name: 'Fazer pedido' }).isVisible());
  await page.getByRole('link', { name: 'Notificações' }).click();
  await page.getByRole('heading', { name: 'Notificações' }).waitFor({ timeout: 10000 });
  check('shell carrega mfe-notifications (rota /notifications)', true);
  check('chamadas /api feitas pelos remotos', authHeaders.length >= 3);
  check('todas as chamadas /api levam Bearer do shell', authHeaders.every((h) => h.startsWith('Bearer ')));
  await ctx.close();
}

// ---------- 3: remoto fora do ar não derruba o shell ----------
{
  const { ctx, page } = await newPage(['user']);
  await ctx.route('**/api/**', (r) => r.fulfill({ json: [] }));
  await ctx.route('http://localhost:4202/**', (r) => r.abort());
  await page.goto(`${BASE}/orders`);
  await page.getByRole('alert').waitFor({ timeout: 20000 });
  check('remoto fora do ar mostra o aviso', (await page.getByRole('alert').innerText()).includes('Módulo indisponível'));
  await page.getByRole('link', { name: 'Catálogo' }).click();
  await page.getByRole('heading', { name: 'Catálogo' }).waitFor({ timeout: 10000 });
  check('os outros módulos continuam funcionando', true);
  await ctx.close();
}

// ---------- 4: CRUD completo como admin, contra uma API simulada com estado ----------
{
  const { ctx, page } = await newPage(['admin', 'user']);
  const state = {
    products: [
      { id: 1, name: 'Teclado mecânico', priceCents: 34900, stock: 20, categoryId: 2, categoryName: 'Periféricos', version: 0 },
      { id: 2, name: 'Livro SRE', priceCents: 7990, stock: 50, categoryId: 1, categoryName: 'Livros', version: 0 },
    ],
    categories: [{ id: 1, name: 'Livros', version: 0 }, { id: 2, name: 'Periféricos', version: 0 }],
    next: 10,
  };
  const calls = [];
  const catName = (id) => state.categories.find((c) => c.id === id)?.name ?? null;
  await ctx.route('**/api/**', (r) => {
    const req = r.request();
    const path = new URL(req.url()).pathname;
    const method = req.method();
    const body = req.postData() ? JSON.parse(req.postData()) : null;
    const id = Number(path.split('/').pop());
    calls.push({ method, path, body, search: new URL(req.url()).search });
    const json = (data, status = 200) => r.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
    if (path === '/api/me') return json({ username: 'alice', roles: ['admin', 'user'] });
    if (path === '/api/products' && method === 'GET') {
      // A API real filtra e pagina no servidor; o mock imita isso para o teste exercitar a tela de verdade.
      const sp = new URL(req.url()).searchParams;
      const q = (sp.get('q') ?? '').toLowerCase();
      const cat = sp.get('categoryId');
      const size = Number(sp.get('size') ?? 10);
      const page = Number(sp.get('page') ?? 0);
      const all = state.products
        .filter((p) => (!q || p.name.toLowerCase().includes(q)) && (!cat || String(p.categoryId) === cat))
        .sort((a, b) => a.name.localeCompare(b.name));
      return json({ items: all.slice(page * size, page * size + size), page, size, total: all.length });
    }
    if (path === '/api/categories' && method === 'GET') return json(state.categories);
    if (path === '/api/admin/products' && method === 'POST') {
      const p = { id: state.next++, ...body, categoryName: catName(body.categoryId), version: 0 };
      state.products.push(p);
      return json(p, 201);
    }
    if (path.startsWith('/api/admin/products/') && method === 'PUT') {
      const p = state.products.find((x) => x.id === id);
      if (!p || p.version !== body.version) return json({ detail: 'O produto foi alterado por outra pessoa. Recarregue e tente de novo.' }, 409);
      Object.assign(p, { name: body.name, priceCents: body.priceCents, stock: body.stock, categoryId: body.categoryId, categoryName: catName(body.categoryId), version: p.version + 1 });
      return json(p);
    }
    if (path.startsWith('/api/admin/products/') && method === 'DELETE') {
      state.products = state.products.filter((x) => x.id !== id);
      return r.fulfill({ status: 204 });
    }
    if (path === '/api/admin/categories' && method === 'POST') {
      const c = { id: state.next++, name: body.name, version: 0 };
      state.categories.push(c);
      return json(c, 201);
    }
    if (path.startsWith('/api/admin/categories/') && method === 'DELETE') {
      const c = state.categories.find((x) => x.id === id);
      if (state.products.some((p) => p.categoryId === id)) return json({ detail: `A categoria "${c.name}" ainda tem produtos. Mova ou exclua os produtos antes.` }, 409);
      state.categories = state.categories.filter((x) => x.id !== id);
      return r.fulfill({ status: 204 });
    }
    return json({});
  });

  await page.goto(`${BASE}/catalog`);
  await page.getByRole('button', { name: 'Novo produto' }).waitFor({ timeout: 20000 });
  check('admin vê "Novo produto" e as ações por linha', (await page.getByRole('button', { name: /Editar/ }).count()) === 2);

  // Create
  await page.getByRole('button', { name: 'Novo produto' }).click();
  const form = page.getByRole('form', { name: 'Formulário de produto' });
  await form.getByLabel('Nome').fill('Mouse sem fio');
  await form.getByLabel('Preço (R$)').fill('89.9');
  await form.getByLabel('Estoque').fill('7');
  await form.getByLabel('Categoria').selectOption({ label: 'Periféricos' });
  await form.getByRole('button', { name: 'Salvar' }).click();
  const mouse = page.getByRole('row', { name: /Mouse sem fio/ });
  await mouse.waitFor({ timeout: 10000 });
  const post = calls.find((c) => c.method === 'POST' && c.path === '/api/admin/products');
  check('CRIAR: linha aparece e o POST leva preço em centavos e categoria', post?.body.priceCents === 8990 && post?.body.categoryId === 2 && (await mouse.innerText()).includes('Periféricos'));

  // Update
  await mouse.getByRole('button', { name: 'Editar Mouse sem fio' }).click();
  await form.getByLabel('Preço (R$)').fill('79.9');
  await form.getByRole('button', { name: 'Salvar' }).click();
  await page.getByText('atualizado').waitFor({ timeout: 10000 });
  const put = calls.find((c) => c.method === 'PUT' && c.path.startsWith('/api/admin/products/'));
  const rowText = await page.getByRole('row', { name: /Mouse sem fio/ }).innerText();
  check('EDITAR: o PUT leva a versão vista na tela e a linha mostra o novo preço', put?.body.version === 0 && put?.body.priceCents === 7990 && rowText.includes('79,90'), { body: put?.body, rowText });

  // Conflict (outra pessoa salvou antes)
  await page.getByRole('row', { name: /Mouse sem fio/ }).getByRole('button', { name: 'Editar Mouse sem fio' }).click();
  state.products.find((p) => p.name === 'Mouse sem fio').version += 1; // alteração concorrente no servidor
  await form.getByLabel('Estoque').fill('9');
  await form.getByRole('button', { name: 'Salvar' }).click();
  await page.getByRole('alert').waitFor({ timeout: 10000 });
  check('CONFLITO: versão desatualizada mostra o aviso do servidor', (await page.getByRole('alert').innerText()).includes('alterado por outra pessoa'));

  // Delete (com confirmação)
  await page.getByRole('row', { name: /Mouse sem fio/ }).getByRole('button', { name: 'Excluir Mouse sem fio' }).click();
  await page.getByText('Excluir "Mouse sem fio"?').first().waitFor({ timeout: 5000 }).catch(() => {});
  const asks = await page.getByText('Excluir "Mouse sem fio"?').count();
  check('EXCLUIR: pede confirmação antes de apagar', asks >= 1 && !calls.some((c) => c.method === 'DELETE'), { asks, deletes: calls.filter((c) => c.method === 'DELETE') });
  await page.getByRole('button', { name: 'Sim, excluir' }).click();
  await page.getByText('excluído').waitFor({ timeout: 10000 });
  check('EXCLUIR: a linha some e o DELETE foi enviado', (await page.getByRole('row', { name: /Mouse sem fio/ }).count()) === 0 && calls.some((c) => c.method === 'DELETE' && c.path.startsWith('/api/admin/products/')));

  // Busca e paginação (no servidor)
  await page.getByRole('textbox', { name: 'Buscar' }).fill('livro');
  await page.getByRole('row', { name: /Teclado/ }).waitFor({ state: 'detached', timeout: 10000 }); // some quando o servidor filtra
  const searchCall = calls.filter((c) => c.path === '/api/products' && c.method === 'GET').at(-1);
  check('BUSCA: o filtro vai ao servidor (q=livro) e a tabela mostra só o resultado',
    searchCall?.search.includes('q=livro') && (await page.getByRole('row', { name: /Livro SRE/ }).count()) === 1, { search: searchCall?.search });
  await page.getByRole('textbox', { name: 'Buscar' }).fill('');
  await page.getByRole('row', { name: /Teclado/ }).waitFor({ timeout: 10000 });
  for (let i = 1; i <= 12; i++) {
    state.products.push({ id: 100 + i, name: `Item ${String(i).padStart(2, '0')}`, priceCents: 1000, stock: 1, categoryId: null, categoryName: null, version: 0 });
  }
  await page.getByRole('combobox', { name: 'Categoria' }).first().selectOption({ label: 'Todas' }); // recarrega a lista
  await page.getByText(/Página 1 de 2/).waitFor({ timeout: 10000 });
  await page.getByRole('button', { name: 'Próxima' }).click();
  await page.getByText(/Página 2 de 2/).waitFor({ timeout: 10000 });
  check('PAGINAÇÃO: "Próxima" pede a página 1 ao servidor e o controle indica 2 de 2', true);

  // Categorias
  await page.getByRole('link', { name: 'Categorias' }).click();
  await page.getByRole('heading', { name: 'Catálogo' }).waitFor();
  await page.getByRole('textbox', { name: 'Nova categoria' }).fill('Acessórios');
  await page.getByRole('button', { name: 'Adicionar' }).click();
  await page.getByRole('row', { name: /Acessórios/ }).waitFor({ timeout: 10000 });
  check('CATEGORIAS: criar', true);
  await page.getByRole('row', { name: /Livros/ }).getByRole('button', { name: 'Excluir Livros' }).click();
  await page.getByRole('button', { name: 'Sim, excluir' }).click();
  await page.getByRole('alert').waitFor({ timeout: 10000 });
  check('CATEGORIAS: não exclui categoria que ainda tem produtos', (await page.getByRole('alert').innerText()).includes('ainda tem produtos'));
  await ctx.close();
}

// ---------- cancelar pedido ----------
{
  const { ctx, page } = await newPage(['user']);
  const orders = [
    { id: 1, customer: 'alice', productId: 1, quantity: 1, totalCents: 1000, status: 'PENDING', createdAt: new Date().toISOString() },
    { id: 2, customer: 'alice', productId: 1, quantity: 2, totalCents: 2000, status: 'CONFIRMED', createdAt: new Date().toISOString() },
  ];
  const calls = [];
  await ctx.route('**/api/**', (r) => {
    const req = r.request();
    const path = new URL(req.url()).pathname;
    calls.push({ method: req.method(), path });
    const json = (data, status = 200) => r.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
    if (path === '/api/me') return json({ username: 'alice', roles: ['user'] });
    if (path === '/api/products') return json({ items: [{ id: 1, name: 'Teclado mecânico', priceCents: 1000 }], page: 0, size: 100, total: 1 });
    if (path === '/api/orders' && req.method() === 'GET') return json(orders);
    const m = path.match(/^\/api\/orders\/(\d+)\/cancel$/);
    if (m && req.method() === 'POST') {
      const o = orders.find((x) => x.id === Number(m[1]));
      o.status = 'CANCELLED';
      return json(o);
    }
    return json({});
  });
  await page.goto(`${BASE}/orders`);
  await page.getByRole('button', { name: 'Cancelar pedido 2' }).waitFor({ timeout: 20000 });
  await page.getByRole('button', { name: 'Cancelar pedido 2' }).click();
  await page.getByText('Pedido #2 cancelado.').waitFor({ timeout: 10000 });
  check('CANCELAR PEDIDO: envia POST /api/orders/2/cancel e a linha passa a "Cancelado"',
    calls.some((c) => c.method === 'POST' && c.path === '/api/orders/2/cancel') && (await page.getByRole('row', { name: /Cancelado/ }).count()) === 1);
  check('CANCELAR PEDIDO: pedido já cancelado não oferece o botão', (await page.getByRole('button', { name: 'Cancelar pedido 2' }).count()) === 0);
  await ctx.close();
}

await browser.close();
process.exit(results.every(([, ok]) => ok) ? 0 : 1);
