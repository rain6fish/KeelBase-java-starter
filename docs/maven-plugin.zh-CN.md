# Maven 插件 — `mvn keelbase:export/register` 一键导出与注册 AI 工具

> 给 Maven 团队把「curl 导出 → 复制 JSON → PUT settings」变成一条命令。配合 KeelBase 热更新，**写配置即生效，无需重启**。
> 本插件是 **Maven-only**（maven-plugin 无法被 Gradle 生成 goal 描述）；Gradle 团队用 HTTP 端点或脚本（见[快速开始](quickstart.zh-CN.md)）。

## 1. 两个 goal

| Goal | 命令 | 做什么 |
|---|---|---|
| `export` | `mvn keelbase:export` | 调运行中应用的 `GET /keelbase/proxy-tools/export` → 校验（tools 非空/name 唯一/riskLevel 合法）→ 写 `target/ai_proxy_tools.json` |
| `register` | `mvn keelbase:register` | 在 export 基础上：admin 登录 KeelBase → `PUT /api/v1/settings/ai_proxy_tools`（value=导出 JSON 字符串）→ 热更新生效 |

## 2. 用法

```bash
# 前置：应用已在跑（默认 localhost:8081）；KeelBase 已在跑（register 时需要，默认 localhost:3000）

# 只导出到文件
mvn keelbase:export

# 导出 + 写入 KeelBase（默认 admin / Admin@1234）
mvn keelbase:register

# 覆盖参数（-D 或 <configuration>）
mvn keelbase:register -Dkeelbase.appUrl=http://localhost:8081 \
                      -Dkeelbase.keelbaseUrl=http://localhost:3000 \
                      -Dkeelbase.username=admin -Dkeelbase.password=Secret@123
```

产物 `target/ai_proxy_tools.json` 可直接复查 / 版本管理。

## 3. 参数

| 参数 | 默认 | 说明 |
|---|---|---|
| `keelbase.appUrl` | `http://localhost:8081` | 运行中的应用地址（服务器根，工具 path 为完整路径约定） |
| `keelbase.output` | `target/ai_proxy_tools.json` | export 写文件路径 |
| `keelbase.keelbaseUrl` | `http://localhost:3000` | KeelBase 服务根（register 用） |
| `keelbase.username` / `keelbase.password` | `admin` / `Admin@1234` | 管理台登录凭据（register 用，生产用环境变量注入） |

## 4. 完整闭环（Maven 团队）

```bash
mvn keelbase:register   # 导出 → 校验 → 写 settings
# KeelBase 热更新：无需重启，下次 AI 对话即用新工具
mvn verify              # 构建时自动校验导出契约（export 默认绑定 verify 阶段）
```

> 依赖：需 Maven 3.9+ / JDK 17+。插件走运行时 HTTP 导出（复用应用的运行时扫描器），因此应用必须已启动。
