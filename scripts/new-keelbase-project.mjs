#!/usr/bin/env node

// SPDX-License-Identifier: Apache-2.0
/**
 * 从 keelbase-java-skeleton 生成一个「已接治理的 AI 工具」Spring Boot 项目（借鉴主仓 keelbase-init 模板模式）。
 *
 * 用法:
 *   node scripts/new-keelbase-project.mjs --artifactId keelbase-app --audience legacy-app
 *   node scripts/new-keelbase-project.mjs --artifactId my-tools --package cn.acme --port 8082 --dir ./my-tools
 *
 * 参数:
 *   --artifactId   项目名（默认 keelbase-app；决定 {{Domain}} 类名）
 *   --package      Java 包名（默认 cn.example；决定目录与 package 声明）
 *   --audience     目标系统 audience（默认 legacy-app；须等于 KeelBase ai_proxy_tools 顶层 audience）
 *   --port         端口（默认 8081）
 *   --groupId      Maven groupId（默认取 package 前两段）
 *   --dir          输出目录（默认 ./{artifactId}；已存在则拒绝，防覆盖）
 *
 * 零依赖（node:fs），生成后打印下一步。starter 版本从根 pom.xml 自动读取。
 */
import { promises as fs } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SKELETON = path.join(ROOT, 'keelbase-java-skeleton');

async function readStarterVersion() {
  const pom = await fs.readFile(path.join(ROOT, 'pom.xml'), 'utf8');
  const m = pom.match(/<version>([^<]+)<\/version>/);
  return m ? m[1] : '0.1.5';
}

function arg(args, key, fallback) {
  for (let i = 0; i < args.length - 1; i++) {
    if (args[i] === key) {
      return args[i + 1];
    }
  }
  return fallback;
}

function toPascal(s) {
  const base = s.replace(/[-_.](.)/g, (_, c) => c.toUpperCase());
  return base.charAt(0).toUpperCase() + base.slice(1);
}

async function exists(p) {
  try {
    await fs.access(p);
    return true;
  } catch {
    return false;
  }
}

async function walk(dir) {
  const out = [];
  for (const e of await fs.readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      out.push(...(await walk(full)));
    } else if (e.isFile()) {
      out.push(full);
    }
  }
  return out;
}

async function main() {
  const args = process.argv.slice(2);
  const artifactId = arg(args, '--artifactId', 'keelbase-app');
  const packageName = arg(args, '--package', 'cn.example');
  const audience = arg(args, '--audience', 'legacy-app');
  const port = arg(args, '--port', '8081');
  const groupId = arg(args, '--groupId', packageName.split('.').slice(0, 2).join('.'));
  const target = arg(args, '--dir', path.resolve(artifactId));
  const version = await readStarterVersion();

  const Domain = toPascal(artifactId);
  const packageDir = packageName.replaceAll('.', '/');
  const vars = {
    '{{groupId}}': groupId,
    '{{artifactId}}': artifactId,
    '{{version}}': version,
    '{{packagePath}}': packageName,
    '{{packageDir}}': packageDir,
    '{{Domain}}': Domain,
    '{{domain}}': artifactId.toLowerCase(),
    '{{audience}}': audience,
    '{{port}}': port,
  };

  if (await exists(target)) {
    console.error(`[错误] 目标目录已存在（${target}）——避免覆盖，请用 --dir 指定新目录或删除后重跑`);
    process.exit(1);
  }

  const files = await walk(SKELETON);
  for (const f of files) {
    const rel = path.relative(SKELETON, f)
      .replaceAll('{{packageDir}}', packageDir)
      .replaceAll('{{Domain}}', Domain);
    let content = await fs.readFile(f, 'utf8');
    for (const [k, v] of Object.entries(vars)) {
      content = content.replaceAll(k, v);
    }
    const dest = path.join(target, rel);
    await fs.mkdir(path.dirname(dest), { recursive: true });
    await fs.writeFile(dest, content, 'utf8');
  }

  console.log(`\n✅ 已生成 KeelBase 受治理 AI 工具项目: ${target}`);
  console.log(`   artifactId=${artifactId}  package=${packageName}  audience=${audience}  port=${port}  starter=${version}`);
  console.log('\n下一步：');
  console.log(`  cd ${target}`);
  console.log('  export KEELBASE_DELEGATION_SECRET=<与 KeelBase 共享的密钥，≥32字节>');
  console.log('  mvn spring-boot:run        # 启动（读工具 R1 自动 / 写工具 R3 确认 + 补偿端点已就绪）');
  console.log('  mvn keelbase:register      # 导出 + 写入 KeelBase（热更新生效，免重启）');
}

main().catch((e) => {
  console.error(`\nFAIL: ${e.message}`);
  process.exit(1);
});
