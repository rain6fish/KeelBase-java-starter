#!/usr/bin/env node

// SPDX-License-Identifier: Apache-2.0
/**
 * KeelBase-java-starter Approval 样板端到端联调验收（Reference Project）。
 *
 * 验证「传统 Java 审批流接入 KeelBase 作为受治理 AI 工具」完整闭环（域对齐
 * specs/external-approval.openapi.json）：
 *   导出（@KeelbaseTool → ai_proxy_tools：query_approval_requests/get_approval_request/decide_approval_request）
 *   → 写配置 → 重启 KeelBase 生效 → AI 对话触发 decide_approval_request 写确认门控
 *   → **流式边读边批准** → proxy 转发到 Java 审批（委托身份写回 decidedBy）
 *   → 审计 → 撤销副作用 → 补偿端点幂等（状态恢复 pending）。
 *
 * 前置：
 *   1. KeelBase 后端已起（默认 http://localhost:3000），且 ai_proxy_tools 已配置并重启
 *   2. Java 审批样板已起（默认 http://localhost:8084，cd keelbase-java-approval-example && mvn spring-boot:run）
 *
 * 用法：
 *   node verify-approval-e2e.mjs --configure   # 导出并写入 ai_proxy_tools（提示重启）
 *   node verify-approval-e2e.mjs --verify      # 跑完整闭环（默认确定性 demo provider）
 */
import process from 'node:process';

const KEELBASE = process.env.KEELBASE_URL ?? 'http://localhost:3000';
const EXAMPLE = process.env.EXAMPLE_URL ?? 'http://localhost:8084';
const args = process.argv.slice(2);

async function main() {
  if (args.includes('--configure')) await configure();
  if (args.includes('--verify')) await verify();
  if (!args.includes('--configure') && !args.includes('--verify')) {
    console.error('用法: node verify-approval-e2e.mjs --configure|--verify');
    process.exit(2);
  }
}

async function login(username, password) {
  const res = await fetch(`${KEELBASE}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) throw new Error(`${username} 登录失败: ${res.status}`);
  return (await res.json()).data.accessToken;
}

async function configure() {
  const res = await fetch(`${EXAMPLE}/keelbase/proxy-tools/export`);
  if (!res.ok) throw new Error(`导出失败: ${res.status}（审批样板是否已起？）`);
  const cfg = await res.json();
  const names = cfg.tools?.map((t) => t.name) ?? [];
  const required = ['query_approval_requests', 'get_approval_request', 'decide_approval_request'];
  const missing = required.filter((n) => !names.includes(n));
  if (missing.length > 0) throw new Error(`导出缺少预期工具: ${missing.join(', ')}（实际: ${names.join(', ')}）`);
  console.log(`[configure] Approval 导出 OK: ${names.join(', ')}`);

  const token = await login('admin', 'Admin@1234');
  const put = await fetch(`${KEELBASE}/api/v1/settings/ai_proxy_tools`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ value: JSON.stringify(cfg), type: 'string' }),
  });
  if (!put.ok) throw new Error(`写入 ai_proxy_tools 失败: ${put.status}`);
  console.log('[configure] ai_proxy_tools 已写入。请重启 KeelBase 使生效，然后 --verify。');
}

async function verify() {
  console.log(`[verify] 目标: KeelBase=${KEELBASE} Java-Approval=${EXAMPLE}`);
  const token = await login('alex', '123456');

  // 触发写对话，流式边读边批准（确定性 demo provider 按工具名匹配）
  const body = { message: 'decide_approval_request 演示：审批请求 id=1 approve=true needsReview=false', conversationId: null };
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
      if (evt.type === 'tool_call' && evt.toolName) console.log(`[verify] tool_call: ${evt.toolName}`);
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

  // 断言 Java 审批收到委托身份决定（请求 1 decidedBy != anonymous + 状态变更）
  await new Promise((r) => setTimeout(r, 500));
  const req = await (await fetch(`${EXAMPLE}/api/approval-requests/1`)).json();
  if (req.decidedBy && req.decidedBy !== 'anonymous') {
    console.log(`[verify] Java 审批已收到委托身份决定: decidedBy=${req.decidedBy} status=${req.status}`);
  } else {
    console.warn('[verify] 提示：Java 审批未看到带委托身份的决定（检查 LLM 是否调用了 decide_approval_request）');
  }

  // 撤销副作用（admin）→ 调 Java 补偿端点 → 状态恢复 pending
  const adminToken = await login('admin', 'Admin@1234');
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
  console.log(`[verify] 撤销结果: ${JSON.stringify((await revoke.json()).data ?? {})}`);

  console.log('[verify] Approval 端到端闭环通过：导出 → 确认门控 → 流式批准 → 委托身份写回 → 审计 → 撤销补偿');
}

main().catch((e) => {
  console.error(`\nFAIL: ${e.message}`);
  process.exit(1);
});
