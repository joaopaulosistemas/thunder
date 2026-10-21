import { expect, Page, test } from '@playwright/test';

// Contas do realm (infra/keycloak/shop-realm.json): senha igual ao usuario.
async function login(page: Page, username: string): Promise<void> {
  const problems: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') problems.push(`console: ${m.text().slice(0, 200)}`); });
  page.on('requestfailed', (r) => problems.push(`falhou: ${r.method()} ${r.url().slice(0, 120)} ${r.failure()?.errorText}`));
  page.on('response', (r) => { if (r.status() >= 400) problems.push(`HTTP ${r.status()} ${r.url().slice(0, 120)}`); });
  await page.goto('/');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(username);
  await page.locator('#kc-login').click();
  try {
    await expect(page.getByRole('heading', { name: 'Catálogo' })).toBeVisible();
  } catch (e) {
    // Diagnostico no proprio erro: sem acesso aos artefatos do CI, e o unico jeito de saber o que o navegador viu.
    const text = (await page.locator('body').innerText().catch(() => '?')).replace(/\s+/g, ' ').slice(0, 400);
    throw new Error(`login de ${username} nao chegou ao catalogo. url=${page.url()} texto="${text}" problemas=${JSON.stringify(problems.slice(0, 12))}`);
  }
}

test('sem sessao, o shell manda para o login do Keycloak', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/realms\/shop\//);
});

test('a API exige token pelo gateway (401)', async ({ request }) => {
  expect((await request.get('/api/products')).status()).toBe(401);
});

test('catalogo pagina e busca no servidor', async ({ page }) => {
  await login(page, 'alice');
  await expect(page.getByRole('cell', { name: /Teclado mecânico/ })).toBeVisible();
  await expect(page.getByText(/Página 1 de \d+/)).toBeVisible();

  await page.getByLabel('Buscar').fill('livro');
  await expect(page.getByRole('cell', { name: /Teclado mecânico/ })).toHaveCount(0);
  await expect(page.getByRole('cell', { name: /Site Reliability/ })).toBeVisible();
});

test('admin cria, edita e exclui produto; cliente nao ve os botoes', async ({ page }) => {
  await login(page, 'alice');
  await expect(page.getByRole('button', { name: 'Novo produto' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Sair' }).click();

  await login(page, 'bob');
  const name = `E2E ${Date.now()}`;
  await page.getByRole('button', { name: 'Novo produto' }).click();
  await page.getByLabel('Nome').fill(name);
  await page.getByLabel('Preço (R$)').fill('12.34');
  await page.getByLabel('Estoque').fill('7');
  await page.getByRole('button', { name: 'Salvar' }).click();
  await expect(page.getByText(`Produto "${name}" criado.`)).toBeVisible();

  await page.getByLabel('Buscar').fill(name);
  await page.getByRole('button', { name: `Editar ${name}` }).click();
  await page.getByLabel('Estoque').fill('9');
  await page.getByRole('button', { name: 'Salvar' }).click();
  await expect(page.getByText(`Produto "${name}" atualizado.`)).toBeVisible();

  await page.getByRole('button', { name: `Excluir ${name}` }).click();
  await page.getByRole('button', { name: 'Sim, excluir' }).click();
  await expect(page.getByText(`Produto "${name}" excluído.`)).toBeVisible();
});

test('pedido vira CONFIRMADO via saga Kafka e pode ser cancelado', async ({ page }) => {
  await login(page, 'alice');
  await page.getByRole('link', { name: /Pedidos/ }).click();
  await page.getByLabel('Quantidade').fill('1');
  await page.getByRole('button', { name: 'Fazer pedido' }).click();
  await expect(page.getByRole('status')).toContainText(/Pedido #\d+ recebido/);

  // Assincrono: order -> Kafka -> catalog (reserva) -> Kafka -> order
  const row = page.getByRole('row').filter({ hasText: 'alice' }).first();
  await expect(row.getByText('Confirmado')).toBeVisible();

  await row.getByRole('button', { name: /Cancelar pedido/ }).click();
  await expect(row.getByText('Cancelado')).toBeVisible();
});
