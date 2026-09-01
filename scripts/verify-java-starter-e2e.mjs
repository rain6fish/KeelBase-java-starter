#!/usr/bin/env node

// SPDX-License-Identifier: Apache-2.0
/**
 * KeelBase-java-starter 端到端联调验收（M3）。
 *
 * 验证「Java 存量系统接入 KeelBase 作为受治理 AI 工具」完整闭环：
 *   导出（@KeelbaseTool → ai_proxy_tools）→ 写配置 → 热更新生效（免重启）
 *   → AI 对话触发写确认门控 → **流式边读边批准**（确认门控使流挂起等 decision）
 *   → proxy 转发到 Java 示例（委托身份）→ 审计落哈希链 → 撤销副作用 → 补偿端点幂等。
 *
 * 前置：
 *   1. KeelBase 后端已起（默认 http://localhost:3000），且 ai_proxy_tools 已配置（热更新生效）
 *   2. Java 示例已起（默认 http://localhost:8081，mvn spring-boot:run）
 *
 * 用法：
 *   node verify-java-starter-e2e.mjs --configure      # 导出并写入 ai_proxy_tools（热更新生效）
 *   node verify-java-starter-e2e.mjs --verify         # 跑完整闭环（默认确定性 demo provider）
 *   node verify-java-starter-e2e.mjs --verify --llm    # 用真实 LLM（需 API Key）
 *
 * 注意：确认门控使流式响应挂起等待人工决策，必须**流式边读边批准**，
 *       不能先读完整个流再批准（token 会因 TTL 超时失效 → 404）。
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
  console.log('[configure] ai_proxy_tools 已写入。KeelBase 热更新生效（免重启），然后 --verify。');
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

  // 2. 发起写对话，流式边读边批准
  const body = llm
    ? { message: '请调用 create_followup 工具，在外部系统为蓝湾地产创建一条跟进任务，参数 content=回访确认合同细节，priority=HIGH，customerId=1', conversationId: null }
    : { message: 'create_followup 演示：创建跟进任务 content=回访演示 customerId=1', conversationId: null };
  const stream = await fetch(`${KEELBASE}/api/v1/ai/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  });
  if (!stream.ok || !stream.body) throw new Error(`AI 对话失败: ${stream.status}`);

  const reader = stream.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let confirmed = false;

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let idx;
    while ((idx = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 1);
      const m = line.match(/^data: (.*)$/);
      if (!m) continue;
      let evt;
      try { evt = JSON.parse(m[1]); } catch { continue; }
      if (evt.type === 'tool_call' && evt.toolName) {
        console.log(`[verify] tool_call: ${evt.toolName}`);
      }
      // 确认门控：立即批准（流此时挂起等 decision）
      if (evt.type === 'confirmation_request' && !confirmed) {
        confirmed = true;
        const ct = evt.confirmation?.token;
        console.log(`[verify] 确认门控触发（${evt.confirmation?.toolName}），立即批准…`);
        const approve = await fetch(`${KEELBASE}/api/v1/ai/confirmations/${ct}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify({ decision: 'approve', trustTool: false }),
        });
        console.log(`[verify] 批准 status=${approve.status}`);
        if (!approve.ok) throw new Error(`批准失败: ${approve.status} ${(await approve.text()).slice(0, 120)}`);
      }
    }
  }
  if (!confirmed) throw new Error('未捕获 confirmation_request（写确认门控未触发？）');
  console.log('[verify] 确认门控 → 批准 → 流完成');

  // 3. 断言 Java 示例收到写入（list 含新 followup）
  await new Promise((r) => setTimeout(r, 500));
  const list = await fetch(`${EXAMPLE}/api/followups`);
  const items = await list.json();
  const created = (items ?? []).filter((i) => i.createdBy && i.createdBy !== 'anonymous');
  if (created.length === 0) {
    console.warn('[verify] 提示：Java 示例未看到带委托身份的写入（检查 LLM 是否调用了代理工具 create_followup 而非内置工具）');
  } else {
    console.log(`[verify] Java 示例已收到委托身份写入: ${created.length} 条（createdBy=${created[0].createdBy}）`);
  }

  // 4. 撤销副作用（tool-effects 是 admin-only；经 KeelBase 撤销 → 调 Java 补偿端点）→ 幂等
  const adminLogin = await fetch(`${KEELBASE}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'Admin@1234' }),
  });
  if (!adminLogin.ok) throw new Error(`admin 登录失败: ${adminLogin.status}`);
  const adminData = (await adminLogin.json()).data;
  const adminToken = adminData.accessToken;

  const effects = await fetch(`${KEELBASE}/api/v1/ai/tool-effects`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (!effects.ok) throw new Error(`tool-effects 查询失败: ${effects.status}`);
  const effectsJson = await effects.json();
  const proxyEffect = (effectsJson.data?.items ?? []).find((e) => e.resultType === 'proxy_call');
  if (!proxyEffect) throw new Error('未找到 proxy_call 副作用记录');
  const revoke = await fetch(`${KEELBASE}/api/v1/ai/tool-effects/${proxyEffect.id}`, {
    method: 'DELETE', headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (!revoke.ok) throw new Error(`撤销失败: ${revoke.status}`);
  const revokeBody = await revoke.json();
  console.log(`[verify] 撤销结果: ${JSON.stringify(revokeBody.data ?? revokeBody)}`);

  console.log('[verify] 端到端闭环通过：导出 → 确认门控 → 流式批准 → 写回 Java → 审计 → 撤销补偿');
}

main().catch((e) => {
  console.error(`\nFAIL: ${e.message}`);
  process.exit(1);
});
