import { expect, Page, test } from '@playwright/test';

// Contas do realm importado (infra/keycloak/shop-realm.json): senha igual ao usuario.
async function login(page: Page, username: string): Promise<void> {
  await page.goto('/');
  // O app redireciona para a tela de login do Keycloak (Authorization Code + PKCE).
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(username);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('heading', { name: 'Loja modular' })).toBeVisible();
}

test('sem sessao, o app manda para o login do Keycloak', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/realms\/shop\//);
  await expect(page.locator('#username')).toBeVisible();
});

test('senha errada nao entra', async ({ page }) => {
  await page.goto('/');
  await page.locator('#username').fill('alice');
  await page.locator('#password').fill('senha-errada');
  await page.locator('#kc-login').click();
  await expect(page).toHaveURL(/\/realms\/shop\//);
  await expect(page.getByRole('heading', { name: 'Loja modular' })).toHaveCount(0);
});

test('cliente faz um pedido e recebe a notificacao vinda do Kafka', async ({ page }) => {
  await login(page, 'alice');

  await page.getByLabel('Quantidade').fill('2');
  await page.getByRole('button', { name: 'Pedir' }).click();

  await expect(page.getByRole('status')).toContainText(/Pedido #\d+ criado/);
  // Assincrono: backend -> Kafka -> consumidor -> /api/notifications
  await expect(page.getByText(/Pedido #\d+ de alice confirmado/).first()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Meus ultimos pedidos' })).toBeVisible();
});

test('backend e navegador concordam sobre quem e o usuario e suas roles', async ({ page }) => {
  await login(page, 'alice');

  const token = page.locator('section.token');
  await expect(token).toContainText('Backend enxergou');
  await expect(token).toContainText('alice');
  await expect(token).toContainText('user');
});

test('cliente comum nao ve o painel admin; admin ve', async ({ page }) => {
  await login(page, 'alice');
  await expect(page.getByRole('heading', { name: 'Painel admin' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Sair' }).click();
  await expect(page.locator('#username')).toBeVisible();

  await login(page, 'bob');
  await expect(page.getByRole('heading', { name: 'Painel admin' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Ultimos pedidos (todos)' })).toBeVisible();
});

test('a API exige token (401) e a role admin (403), pelo gateway', async ({ page, request }) => {
  // Sem token: 401 (as chamadas passam pelo Nginx, como no uso real)
  const semToken = await request.get('/api/products');
  expect(semToken.status()).toBe(401);

  // Captura o token REAL que o app mandou depois do login da alice (cliente comum)
  const chamadaDoApp = page.waitForRequest((req) => req.url().endsWith('/api/me'));
  await login(page, 'alice');
  const bearer = (await chamadaDoApp).headers()['authorization'];
  expect(bearer).toMatch(/^Bearer ey/);

  const headers = { Authorization: bearer };
  expect((await request.get('/api/me', { headers })).status()).toBe(200);
  expect((await request.get('/api/admin/summary', { headers })).status()).toBe(403);
});
