#!/usr/bin/env node

// SPDX-License-Identifier: Apache-2.0
/**
 * 本地调试 / 接入自检（不依赖 KeelBase 后端）：
 * 验证 Java 参考项目（keelbase-java-example / -crm / -pm / -approval）的接入健康——
 * 委托验签、工具导出契约、受保护路径门控，纯本地即可跑。
 *
 * 用法:
 *   node scripts/verify-java-local.mjs                        # 默认 http://localhost:8081
 *   node scripts/verify-java-local.mjs http://localhost:8084  # 指定参考项目
 *   KB_SECRET=... node scripts/verify-java-local.mjs          # 指定 DELEGATION_SECRET
 *
 * 检查:
 *   ① GET /keelbase/status          → 接入健康度（delegation/export/tools/health）
 *   ② GET /keelbase/proxy-tools/export → 工具契约（读 R1 自动 / 写 R3 确认 + revokePath）
 *   ③ 受保护补偿路径                → 无 token 401；构造委托 token 后幂等 200（验签通过）
 */
import { createHmac } from 'node:crypto';

const BASE = process.argv[2] || process.env.KB_BASE || 'http://localhost:8081';
const SECRET = process.env.KB_SECRET || '0123456789012345678901234567890123456789012345678901234567890123';
const AUDIENCE = process.env.KB_AUDIENCE || 'legacy-crm';

const C = { reset: '\x1b[0m', green: '\x1b[32m', red: '\x1b[31m', dim: '\x1b[2m' };

function b64url(s) { return Buffer.from(s).toString('base64url'); }

/** 本地构造委托 JWT（HS256，对齐 DelegationAuthFilter：aud/iss/exp）。 */
function delegationToken() {
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const now = Math.floor(Date.now() / 1000);
  const payload = b64url(JSON.stringify({ sub: 'local:42', aud: AUDIENCE, iss: 'keelbase', iat: now, exp: now + 120 }));
  const sig = createHmac('sha256', SECRET).update(`${header}.${payload}`).digest('base64url');
  return `${header}.${payload}.${sig}`;
}

async function get(path, token, method = 'GET') {
  const res = await fetch(BASE + path, {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const body = await res.json().catch(() => ({}));
  return { status: res.status, body };
}

function report(name, ok, detail) {
  console.log(`  ${ok ? `${C.green}✓${C.reset}` : `${C.red}✗${C.reset}`} ${name.padEnd(26)} ${C.dim}${detail}${C.reset}`);
  return ok;
}

async function main() {
  console.log(`${C.green}KeelBase Java 本地接入自检${C.reset}`);
  console.log(`  目标: ${BASE}  audience=${AUDIENCE}  不依赖 KeelBase 后端\n`);
  let pass = 0;
  let fail = 0;

  // ① 接入健康度
  try {
    const { status, body } = await get('/keelbase/status');
    if (status !== 200) {
      fail += 1; report('status 端点', false, `HTTP ${status}`);
    } else {
      const health = body.health?.status ?? '?';
      const toolCount = body.tools?.count ?? 0;
      const auditConfigured = body.audit?.configured ?? false;
      pass += report('接入健康度', true,
        `health=${health} tools=${toolCount} audit=${auditConfigured ? 'on' : 'off'} errors=${(body.errors ?? []).length}`);
      if (body.errors?.length) {
        for (const e of body.errors) console.log(`       ${C.red}→ ${e}${C.reset}`);
      }
    }
  } catch (e) {
    fail += 1; report('status 端点', false, e.message);
  }

  // ② 工具导出契约
  try {
    const { status, body } = await get('/keelbase/proxy-tools/export');
    if (status !== 200) {
      fail += 1; report('工具导出', false, `HTTP ${status}`);
    } else {
      const tools = body.tools ?? [];
      const write = tools.filter((t) => ['POST', 'PUT', 'PATCH', 'DELETE'].includes(t.method));
      const revocable = write.filter((t) => t.revokePath);
      pass += report('工具导出契约', true,
        `${tools.length} 工具（读 ${tools.length - write.length} / 写 ${write.length}，可撤销 ${revocable.length}）`);
      if (write.length > 0 && revocable.length === 0) {
        console.log(`       ${C.red}→ 写工具均无 revokePath——AI 写副作用将无法撤销${C.reset}`);
      }
    }
  } catch (e) {
    fail += 1; report('工具导出', false, e.message);
  }

  // ③ 受保护路径门控：无 token 401；委托 token 验签后幂等 200
  //    取导出的第一个 revokePath 作为受保护端点（无则跳过）
  try {
    const { body } = await get('/keelbase/proxy-tools/export');
    const revokable = (body.tools ?? []).find((t) => t.revokePath);
    if (!revokable) {
      console.log(`  ${C.dim}· 委托验签门控 跳过（无写工具/无 revokePath 可测）${C.reset}`);
    } else {
      // revokePath 形如 "DELETE /api/compensation/followups/{id}"——取 method + 纯路径
      const m = revokable.revokePath.trim().match(/^(GET|POST|PUT|PATCH|DELETE)\s+(\S+)/);
      const method = m ? m[1] : 'DELETE';
      const path = (m ? m[2] : revokable.revokePath).replace('{id}', '99999');
      const noToken = await get(path, null, method);
      const okNoToken = noToken.status === 401;
      report('受保护路径（无 token）', okNoToken, `${revokable.revokePath} → ${noToken.status}（期望 401）`);
      if (!okNoToken) fail += 1; else pass += 1;

      const withToken = await get(path, delegationToken(), method);
      const okWithToken = withToken.status === 200;
      report('委托验签（构造 token）', okWithToken,
        `→ ${withToken.status} idempotent=${withToken.body?.idempotent}（期望 200 幂等）`);
      if (!okWithToken) fail += 1; else pass += 1;
    }
  } catch (e) {
    fail += 1; report('委托验签', false, e.message);
  }

  console.log(`\n── Result ── ${fail === 0 ? `${C.green}PASS${C.reset}` : `${C.red}FAIL${C.reset}`}（${pass} pass, ${fail} fail）`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((e) => { console.error(e); process.exit(1); });
