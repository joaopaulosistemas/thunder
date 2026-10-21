import { defineConfig, devices } from '@playwright/test';

// E2E da arquitetura de microsservicos (borda HTTPS com certificado da CA local).
//   microservices/infra/tls/gen-certs.sh
//   docker compose -f microservices/docker-compose.yml up -d --build --wait
//   cd e2e && npx playwright test -c playwright.ms.config.ts
export default defineConfig({
  testDir: './tests-ms',
  timeout: 90_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.BASE_URL ?? 'https://localhost:8443',
    ignoreHTTPSErrors: true, // CA local; em producao o certificado e publico
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
