# Boot 2 / Java 8 Adapter — 存量 Java 栈接入路径

Not every legacy team is on Spring Boot 3 + Java 17 yet. This page lays out the two ways a Java system connects to KeelBase, and the zero-code path for Boot 2 / Java 8 teams.

> 不是所有存量团队都已经升到 Spring Boot 3 + Java 17。本文说明 Java 系统接入 KeelBase 的两条路径，以及 Boot 2 / Java 8 团队的**零代码接入**方式。

## 两条路径

| Path | 技术栈 | 方式 | 依赖 |
|---|---|---|---|
| **A — starter (`@KeelbaseTool`)** | Spring Boot 3 + Java 17+ | 注解声明工具 + 委托验签 + 补偿端点 | `keelbase-spring-boot-starter` |
| **B — API proxy (B path)** | **任意技术栈（含 Boot 2 / Java 8）** | OpenAPI 描述 → 受治理 Proxy 工具，零代码接入 | **无**（KeelBase 侧生成） |

`keelbase-java-starter` 的扫描器依赖 Spring 6.1（Boot 3.2+）的 handler API，因此 **A 路径要求 Boot 3 + Java 17**。Boot 2 / Java 8 团队用 **B 路径**——不依赖 starter，把存量 REST API 交给 KeelBase 生成治理工具，同样获得「读 R1 自动 / 写 R3 确认 / 审计 / 撤销」。

## B 路径：Boot 2 / Java 8 接入（零代码）

```bash
# 1. 导出存量 API 的 OpenAPI 3 / Swagger 2 描述（多数框架自带：springdoc / springfox / 手写）

# 2. 生成 B 路径 Proxy 工具配置（KeelBase 主仓 CLI）
node scripts/keelbase-init.mjs --import-openapi-proxy api.json \
  --base-url http://legacy-crm:8080/api --audience legacy-crm

# 3. 把导出的 JSON 写入 KeelBase Settings（ai_proxy_tools），重启 KeelBase
#    → 工具注册：读（GET）R1 自动 / 写（POST·PATCH）R3 需人工确认

# 4. 写操作撤销：在 Proxy 工具配置里配 revokePath 指向存量系统的补偿端点
#    （或 KeelBase 侧软删兜底）——撤销 AI 写副作用
```

- **委托身份**：KeelBase 转发的委托 JWT 是标准 HS256——存量系统（任意语言）可用任意 JWT 库验签，或单独引入 `keelbase-delegation-filter`（该模块只依赖 Spring Web，Boot 2 可编）。
- **审计 / 确认门控**：由 KeelBase 侧统一处理，存量系统无需实现——只提供 REST 端点即可。

## 对比

| 能力 | A 路径（starter） | B 路径（API proxy） |
|---|---|---|
| 工具声明 | `@KeelbaseTool` 注解 | OpenAPI 描述 |
| 委托验签 | DelegationAuthFilter | 需存量系统自验（或引入 filter）|
| 补偿撤销 | KeelBaseCompensationSupport | revokePath 指向存量端点 |
| 技术栈要求 | Boot 3 + Java 17 | **任意**（含 Boot 2 / Java 8）|
| 启动依赖 | starter | 无 |

## 参考项目

- A 路径：`keelbase-java-example` / `-crm-example` / `-pm-example` / `-approval-example`（Boot 3 + Java 17）
- B 路径：KeelBase 主仓 `external-crm-demo`（OpenAPI → Proxy 工具闭环）
