// Gera src/.../version.ts com a versao do package.json, o commit (GIT_SHA ou git) e a data do build.
// Uso: node scripts/gen-version.mjs <arquivo de saida>
import { execSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';

const out = process.argv[2];
if (!out) {
  console.error('uso: node scripts/gen-version.mjs <arquivo de saida>');
  process.exit(1);
}
const { version } = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));
let commit = process.env.GIT_SHA ?? '';
if (!commit) {
  try {
    commit = execSync('git rev-parse HEAD', { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim();
  } catch {
    commit = 'dev';
  }
}
const short = commit === 'dev' ? 'dev' : commit.slice(0, 7);
const content = `// GERADO por scripts/gen-version.mjs (npm run version:gen). Nao edite.
export const APP_VERSION = '${version}';
export const APP_COMMIT = '${short}';
export const APP_BUILT_AT = '${new Date().toISOString()}';
`;
writeFileSync(out, content);
console.log(`version.ts: v${version} (${short})`);
