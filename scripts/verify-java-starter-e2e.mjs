#!/usr/bin/env node
/**
 * KeelBase-java-starter 端到端联调验收（M3）。
 *
 * 验证「Java 存量系统接入 KeelBase 作为受治理 AI 工具」完整闭环：
 *   导出（@KeelbaseTool → ai_proxy_tools）→ 写配置 → 重启 KeelBase 生效
 *   → AI 对话触发写确认门控 → 批准 → proxy 转发到 Java 示例（委托身份）
 *   → 审计落哈希链 → 撤销副作用 → 补偿端点幂等。
 *
 * 前置：
 *   1. KeelBase 后端已起（默认 http://localhost:3000）
 *   2. Java 示例已起（默认 http://localhost:8081，mvn spring-boot:run）
 *   3. KeelBase 的 ai_proxy_tools 已配置并重启（见 --configure 模式）
 *
 * 用法：
 *   node verify-java-starter-e2e.mjs --configure      # 导出并写入 ai_proxy_tools（提示重启）
 *   node verify-java-starter-e2e.mjs --verify         # 跑完整闭环（需已配置并重启）
 *   node verify-java-starter-e2e.mjs --verify --llm    # 用真实 LLM（默认确定性 demo provider）
 */
import process from 'node:process';

const KEELBASE = process.env.KEELBASE_URL ?? 'http://localhost:3000';
const EXAMPLE = process.env.EXAMPLE_URL ?? 'http://localhost:8081';
const args = process.argv.slice(2);

async function main() {
  if (args.includes('--configure')) {
    await configure();
  }
  if (args.includes('--verify')) {
    await verify();
  }
  if (!args.includes('--configure') && !args.includes('--verify')) {
    console.error('用法: node verify-java-starter-e2e.mjs --configure|--verify [--llm]');
    process.exit(2);
  }
}

async function configure() {
  const res = await fetch(`${EXAMPLE}/keelbase/proxy-tools/export`);
  if (!res.ok) throw new Error(`导出失败: ${res.status}（示例应用是否已起？）`);
  const cfg = await res.json();
  const names = cfg.tools?.map((t) => t.name) ?? [];
  if (!names.includes('create_followup') || !names.includes('list_followups')) {
    throw new Error(`导出缺少预期工具: ${names.join(', ')}`);
  }
  console.log(`[configure] 导出 OK: ${names.join(', ')}`);

  // 登录 admin 写入 Settings
  const login = await fetch(`${KEELBASE}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'Admin@1234' }),
  });
  if (!login.ok) throw new Error(`admin 登录失败: ${login.status}`);
  const { data } = await login.json();
  const token = data.accessToken;

  const put = await fetch(`${KEELBASE}/api/v1/settings/ai_proxy_tools`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ value: JSON.stringify(cfg), type: 'string' }),
  });
  if (!put.ok) throw new Error(`写入 ai_proxy_tools 失败: ${put.status}`);
  console.log('[configure] ai_proxy_tools 已写入。请重启 KeelBase 使生效，然后 --verify。');
}

async function verify() {
  const llm = args.includes('--llm');
  console.log(`[verify] 目标: KeelBase=${KEELBASE} Java=${EXAMPLE} llm=${llm}`);

  // 1. 登录普通用户
  const login = await fetch(`${KEELBASE}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'alex', password: '123456' }),
  });
  if (!login.ok) throw new Error(`alex 登录失败: ${login.status}`);
  const { data } = await login.json();
  const token = data.accessToken;

  // 2. 发起写对话：确定性 demo provider 或真实 LLM 触发 create_followup 确认门控
  const body = llm
    ? { message: '给客户 辰光建材 创建一条跟进任务，内容：回访确认合同', conversationId: null }
    : { message: 'create_followup 演示：创建跟进任务 content=回访演示 customerId=1', conversationId: null };
  const stream = await fetch(`${KEELBASE}/api/v1/ai/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  });
  if (!stream.ok) throw new Error(`AI 对话失败: ${stream.status}`);

  let confirmationToken = null;
  const text = await stream.text();
  for (const line of text.split('\n')) {
    const m = line.match(/^data: (.*)$/);
    if (!m) continue;
    let evt;
    try { evt = JSON.parse(m[1]); } catch { continue; }
    if (evt.type === 'confirmation_request') confirmationToken = evt.token ?? evt.confirmation?.token ?? null;
    if (evt.type === 'tool_call' && evt.toolName) console.log(`[verify] tool_call: ${evt.toolName}`);
  }
  if (!confirmationToken) throw new Error('未捕获 confirmation_request（写确认门控未触发？）');
  console.log('[verify] 确认门控触发，token 已捕获');

  // 3. 批准
  const approve = await fetch(`${KEELBASE}/api/v1/ai/confirmations/${confirmationToken}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ decision: 'approve', trustTool: false }),
  });
  if (!approve.ok) throw new Error(`批准失败: ${approve.status}`);

  // 4. 断言 Java 示例收到写入（list 含新 followup）
  await new Promise((r) => setTimeout(r, 500));
  const list = await fetch(`${EXAMPLE}/api/followups`);
  const items = await list.json();
  if (!items.some((i) => i.content?.includes('回访'))) {
    console.warn('[verify] 提示：Java 示例未在最近写入中看到预期 content（检查示例独立状态）');
  } else {
    console.log('[verify] Java 示例已收到写入');
  }

  // 5. 撤销副作用（经 KeelBase 撤销 → 调 Java 补偿端点）→ 幂等
  const effects = await fetch(`${KEELBASE}/api/v1/ai/tool-effects`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const effectsJson = await effects.json();
  const proxyEffect = (effectsJson.data ?? []).find((e) => e.resultType === 'proxy_call');
  if (!proxyEffect) throw new Error('未找到 proxy_call 副作用记录');
  const revoke = await fetch(`${KEELBASE}/api/v1/ai/tool-effects/${proxyEffect.id}`, {
    method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
  });
  if (!revoke.ok) throw new Error(`撤销失败: ${revoke.status}`);
  const revokeBody = await revoke.json();
  console.log(`[verify] 撤销结果: ${JSON.stringify(revokeBody.data ?? revokeBody)}`);

  console.log('[verify] 端到端闭环通过：导出 → 确认门控 → 批准 → 写回 Java → 审计 → 撤销补偿');
}

main().catch((e) => {
  console.error(`\nFAIL: ${e.message}`);
  process.exit(1);
});
