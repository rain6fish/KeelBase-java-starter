/** 本地静态托管（预览 README / SVG 用）：node scripts/serve-docs.mjs → http://localhost:8100 */
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const types = {
  '.svg': 'image/svg+xml', '.html': 'text/html; charset=utf-8',
  '.md': 'text/plain; charset=utf-8', '.png': 'image/png',
  '.jpg': 'image/jpeg', '.json': 'application/json',
};

http
  .createServer(async (req, res) => {
    let p = decodeURIComponent((req.url ?? '/').split('?')[0]);
    if (p === '/') p = '/README.md';
    try {
      const data = await readFile(path.join(root, p));
      res.writeHead(200, { 'Content-Type': types[path.extname(p)] ?? 'application/octet-stream' });
      res.end(data);
    } catch {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('not found');
    }
  })
  .listen(8100, () => console.log('serve-docs: http://localhost:8100'));
