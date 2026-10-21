// Servidor estático mínimo para o build do Module Federation: os remotos são carregados de outra
// origem (localhost:4201..), então precisam de CORS. Uso: node scripts/serve-dist.mjs
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const APPS = { shell: 4200, 'mfe-catalog': 4201, 'mfe-orders': 4202, 'mfe-notifications': 4203 };
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.css': 'text/css', '.ico': 'image/x-icon' };

for (const [app, port] of Object.entries(APPS)) {
  const root = join(process.cwd(), 'dist', app, 'browser');
  createServer(async (req, res) => {
    const path = normalize(decodeURIComponent(new URL(req.url, 'http://x').pathname)).replace(/^(\.\.[/\\])+/, '');
    let file = join(root, path);
    try {
      if ((await stat(file)).isDirectory()) file = join(file, 'index.html');
    } catch {
      file = join(root, 'index.html'); // fallback de SPA
    }
    try {
      const body = await readFile(file);
      res.writeHead(200, {
        'content-type': TYPES[extname(file)] ?? 'application/octet-stream',
        'access-control-allow-origin': '*',
        'cache-control': 'no-store',
      });
      res.end(body);
    } catch {
      res.writeHead(404).end();
    }
  }).listen(port, () => console.log(`${app} em http://localhost:${port}`));
}
