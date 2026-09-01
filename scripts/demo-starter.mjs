#!/usr/bin/env node

// SPDX-License-Identifier: Apache-2.0
/**
 * 一键演示：KeelBase Starter 接入 + 受治理 AI 工具完整闭环。
 *
 * 把快速开始的手动步骤（起 example → 自检 → 导出 → 写配置 → 验证）变成一条命令：
 *   ① 检查 KeelBase（需已起，默认 http://localhost:3000）
 *   ② （可选 --start-example）后台起 Java 示例（keelbase-java-example，默认 8081）
 *   ③ 本地接入自检（verify-java-local：委托验签 / 工具契约 / 受保护路径门控）
 *   ④ 导出 ai_proxy_tools 并写入 KeelBase（verify-java-starter-e2e --configure，热更新生效）
 *   ⑤ 端到端闭环（--verify：确认门控 → 流式批准 → 写回 Java → 审计 → 撤销补偿）
 *
 * 用法:
 *   node scripts/demo-starter.mjs                     # KeelBase 已起、example 已起
 *   node scripts/demo-starter.mjs --start-example     # 脚本帮你后台起 example（需本机 JDK17+Maven）
 *   KEELBASE_URL=http://localhost:3000 EXAMPLE_URL=http://localhost:8081 node scripts/demo-starter.mjs
 *
 * 前置：KeelBase 后端已启动（主仓库 Server-NestJS：npm run start:dev，或 docker compose up）。
 */
import { spawn, spawnSync } from 'node:child_process';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const EXAMPLE_DIR = path.join(ROOT, 'keelbase-java-example');
const KEELBASE = process.env.KEELBASE_URL ?? 'http://localhost:3000';
const EXAMPLE = process.env.EXAMPLE_URL ?? 'http://localhost:8081';
const args = process.argv.slice(2);
const startExample = args.includes('--start-example');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function reachable(url) {
  try {
    const res = await fetch(url);
    return res.ok || res.status < 500;
  } catch {
    return false;
  }
}

async function waitHealthy(url, what, timeoutMs = 180000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await reachable(url)) return;
    await sleep(2000);
  }
  throw new Error(`${what} 未就绪（${Math.round(timeoutMs / 1000)}s）: ${url}`);
}

function run(script, scriptArgs = []) {
  const res = spawnSync('node', [path.join(ROOT, 'scripts', script), ...scriptArgs], {
    stdio: 'inherit',
    env: { ...process.env, KEELBASE_URL: KEELBASE, EXAMPLE_URL: EXAMPLE },
  });
  if (res.status !== 0) {
    throw new Error(`${script} 失败（exit ${res.status ?? 'signal'})`);
  }
}

let exampleProc = null;

function stopExample(proc) {
  if (!proc) return;
  try {
    if (process.platform === 'win32') {
      spawnSync('taskkill', ['/pid', String(proc.pid), '/T', '/F'], { stdio: 'ignore' });
    } else {
      proc.kill('SIGTERM');
    }
  } catch {
    /* 尽力而为 */
  }
}

async function main() {
  console.log('\n=== KeelBase Starter 一键演示 ===');
  console.log(`KeelBase=${KEELBASE}  Java=${EXAMPLE}  startExample=${startExample}`);

  // ① KeelBase 前置检查
  if (!(await reachable(`${KEELBASE}/health`))) {
    console.error(`\n[提示] KeelBase 未就绪（${KEELBASE}/health 不通）。`);
    console.error('  先启动 KeelBase 后端（主仓库 Server-NestJS）：');
    console.error('    cd Server-NestJS && npm run start:dev');
    console.error('  或容器：docker compose up --build');
    process.exit(1);
  }
  console.log('[1/5] KeelBase 就绪 ✓');

  // ② 起 example（可选）
  if (startExample) {
    console.log(`[2/5] 后台启动 Java 示例（mvn spring-boot:run @ ${EXAMPLE_DIR}）…`);
    exampleProc = spawn('mvn', ['spring-boot:run'], { cwd: EXAMPLE_DIR, stdio: 'ignore', shell: true });
  } else {
    console.log('[2/5] 复用已运行的 Java 示例（不启动；--start-example 可自动起）…');
  }
  await waitHealthy(`${EXAMPLE}/keelbase/status`, 'Java 示例');

  // ③ 本地接入自检
  console.log('\n[3/5] 本地接入自检（委托验签 / 工具契约 / 受保护路径门控，不依赖 KeelBase）…');
  run('verify-java-local.mjs', [EXAMPLE]);

  // ④ 导出 + 写 settings（热更新生效）
  console.log('\n[4/5] 导出 ai_proxy_tools 并写入 KeelBase（热更新，免重启）…');
  run('verify-java-starter-e2e.mjs', ['--configure']);

  // ⑤ 完整闭环
  console.log('\n[5/5] 端到端闭环（确认门控 → 流式批准 → 写回 Java → 审计 → 撤销补偿）…');
  run('verify-java-starter-e2e.mjs', ['--verify']);

  console.log('\n=== 演示完成：KeelBase Starter 接入 + 受治理 AI 工具闭环全通 ===');
  console.log('  后续改工具：mvn keelbase:register（或 PUT /settings/ai_proxy_tools）→ 热更新免重启');
  if (exampleProc) {
    console.log(`  注：Java 示例仍在后台运行（${EXAMPLE}），脚本已记录其进程；演示结束后可手动停止。`);
  }
}

main()
  .then(() => {})
  .catch((e) => {
    console.error(`\nFAIL: ${e.message}`);
    stopExample(exampleProc);
    process.exit(1);
  });
