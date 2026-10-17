#!/usr/bin/env node

// SPDX-License-Identifier: Apache-2.0
/**
 * KeelBase-java-starter PM 样板端到端联调验收（Reference Project）。
 *
 * 验证「传统 Java PM 接入 KeelBase 作为受治理 AI 工具」完整闭环（域对齐
 * specs/external-pm.openapi.json）：
 *   导出（@KeelbaseTool → ai_proxy_tools：query_projects/get_project/create_pm_task）
 *   → 写配置（热更新，无需重启）→ AI 对话触发 create_pm_task 写确认门控
 *   → **流式边读边批准** → proxy 转发到 Java PM（委托身份写回 createdBy）
 *   → 审计 → 撤销副作用 → 补偿端点幂等（任务 cancelled）。
 *
 * 前置：
 *   1. KeelBase 后端已起（默认 http://localhost:3000），且 ai_proxy_tools 已配置（写入即热更新）
 *   2. Java PM 样板已起（默认 http://localhost:8083，cd keelbase-java-pm-example && mvn spring-boot:run）
 *
 * 用法：
 *   node verify-pm-e2e.mjs --configure      # 导出并写入 ai_proxy_tools（写入即热更新）
 *   node verify-pm-e2e.mjs --verify         # 跑完整闭环（默认确定性 demo provider）
 */
import process from 'node:process';

const KEELBASE = process.env.KEELBASE_URL ?? 'http://localhost:3000';
const EXAMPLE = process.env.EXAMPLE_URL ?? 'http://localhost:8083';
// The seed accounts the main repository creates on first start; override for another install.
// 主仓首次启动创建的演示账号；其它环境用环境变量覆盖。
const USER = process.env.KEELBASE_USER ?? 'alex';
const PASS = process.env.KEELBASE_PASS ?? 'Alex@2026$Demo';
const ADMIN = process.env.KEELBASE_ADMIN ?? 'admin';
const ADMIN_PASS = process.env.KEELBASE_ADMIN_PASS ?? 'Admin@2026$KeelBase';
const args = process.argv.slice(2);

async function main() {
  if (args.includes('--configure')) await configure();
  if (args.includes('--verify')) await verify();
  if (!args.includes('--configure') && !args.includes('--verify')) {
    console.error('用法: node verify-pm-e2e.mjs --configure|--verify');
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
  if (!res.ok) throw new Error(`导出失败: ${res.status}（PM 样板是否已起？）`);
  const cfg = await res.json();
  const names = cfg.tools?.map((t) => t.name) ?? [];
  const required = ['query_projects', 'get_project', 'create_pm_task'];
  const missing = required.filter((n) => !names.includes(n));
  if (missing.length > 0) throw new Error(`导出缺少预期工具: ${missing.join(', ')}（实际: ${names.join(', ')}）`);
  console.log(`[configure] PM 导出 OK: ${names.join(', ')}`);

  const token = await login(ADMIN, ADMIN_PASS);
  const put = await fetch(`${KEELBASE}/api/v1/settings/ai_proxy_tools`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ value: JSON.stringify(cfg), type: 'string' }),
  });
  if (!put.ok) throw new Error(`写入 ai_proxy_tools 失败: ${put.status}`);
  console.log('[configure] ai_proxy_tools 已写入（热更新，无需重启），然后 --verify。');
}

async function verify() {
  console.log(`[verify] 目标: KeelBase=${KEELBASE} Java-PM=${EXAMPLE}`);
  const token = await login(USER, PASS);

  // Snapshot what exists before this run: a task left by an earlier verification must not be able
  // to satisfy the assertion below.
  // 先记下本次运行之前已有什么：上一次验证残留的任务不能让下面的断言蒙混过关。
  const before = await (await fetch(`${EXAMPLE}/api/projects/1`)).json();
  const beforeIds = new Set((before.tasks ?? []).map((t) => t.id));

  // 触发写对话，流式边读边批准（确定性 demo provider 按工具名匹配）
  const body = { message: 'create_pm_task 演示：为项目 projectId=1 创建任务 title=升级订单模块依赖', conversationId: null };
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

  // 断言 Java PM 收到带委托身份的写任务（**本次新出现**的任务，createdBy != anonymous）
  // Poll instead of a single fixed sleep: the write lands asynchronously.
  // 轮询而不是固定睡一次：写入是异步落地的。
  const deadline = Date.now() + 10_000;
  let createdTask = null;
  while (Date.now() < deadline && !createdTask) {
    const project = await (await fetch(`${EXAMPLE}/api/projects/1`)).json();
    createdTask = (project.tasks ?? []).find(
      (t) => !beforeIds.has(t.id) && t.createdBy && t.createdBy !== 'anonymous',
    ) ?? null;
    if (!createdTask) await new Promise((r) => setTimeout(r, 250));
  }
  if (!createdTask) {
    throw new Error('Java PM 未看到本次新建且带委托身份的任务（LLM 未调用 create_pm_task，或委托身份没有写回）');
  }
  console.log(`[verify] Java PM 已收到委托身份写入: task=${createdTask.id} createdBy=${createdTask.createdBy}`);

  // 撤销副作用（admin）→ 调 Java 补偿端点 → 任务 cancelled
  const adminToken = await login(ADMIN, ADMIN_PASS);
  const effects = await fetch(`${KEELBASE}/api/v1/ai/tool-effects`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (!effects.ok) throw new Error(`tool-effects 查询失败: ${effects.status}`);
  const effectsJson = await effects.json();
  // Scope the effect to the task this run created: a stale proxy_call would revoke a stale task.
  // 把副作用限定到本次新建的任务：取一条陈旧的 proxy_call 会去撤销一个陈旧的任务。
  const proxyEffect = (effectsJson.data?.items ?? []).find(
    (e) => e.resultType === 'proxy_call' && String(e.resultId) === String(createdTask.id),
  );
  if (!proxyEffect) throw new Error(`未找到本次任务 ${createdTask.id} 的 proxy_call 副作用记录`);
  const revoke = await fetch(`${KEELBASE}/api/v1/ai/tool-effects/${proxyEffect.id}`, {
    method: 'DELETE', headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (!revoke.ok) throw new Error(`撤销失败: ${revoke.status}`);
  console.log(`[verify] 撤销结果: ${JSON.stringify((await revoke.json()).data ?? {})}`);

  // The compensation endpoint must have actually changed the Java row, not merely answered 200.
  // 补偿端点必须真的改了 Java 那一行，而不只是回了 200。
  const after = await (await fetch(`${EXAMPLE}/api/projects/1`)).json();
  const compensated = (after.tasks ?? []).find((t) => t.id === createdTask.id);
  if (!compensated || compensated.status !== 'cancelled') {
    throw new Error(`撤销后任务未变为 cancelled（实际: ${compensated ? compensated.status : '缺失'}）`);
  }
  console.log(`[verify] 补偿生效: task ${createdTask.id} status=cancelled`);

  console.log('[verify] PM 端到端闭环通过：导出 → 确认门控 → 流式批准 → 委托身份写回 → 审计 → 撤销补偿');
}

main().catch((e) => {
  console.error(`\nFAIL: ${e.message}`);
  process.exit(1);
});
