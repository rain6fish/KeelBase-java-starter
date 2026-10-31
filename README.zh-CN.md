# KeelBase Java Starter

> **让存量 Java/Spring 系统获得受治理的 AI 能力**——基于 [KeelBase](https://github.com/rain6fish/KeelBase) 的 Spring Boot Starter：委托身份验签、代理工具导出、撤销补偿端点。Apache-2.0。

> KeelBase 是开源「企业 AI 信任运行时」。用本 Starter，你的存量 Java/Spring REST 端点即可成为 AI 工具——AI 在你的真实数据上干活，全程过 KeelBase 治理层（行级权限 / 写操作人工确认 / 防篡改审计哈希链 / 副作用可撤销）。

<p align="center">
  <img src="docs/keelbase-java-starter-position.svg" alt="KeelBase Java Starter 的位置——处于 KeelBase Trust Runtime 与你的 Java 业务系统之间" width="840">
</p>

**系列长文** —— 这些决定背后的长文，分「AI 执行治理」「AI 审计证据」两条线：[OSChina 文集](https://my.oschina.net/u/4166179/blog)。

<!-- BEGIN keelbase-family:zh sha256:7870abdc3207482b1d3d1a8f7018f0479182ac0d42b052cd6496d3be34f4eba1 -->
## 🧬 KeelBase 家族

<p align="center">
  <img src="https://raw.githubusercontent.com/rain6fish/KeelBase/main/docs/branding/keelbase-family-zh.svg" alt="KeelBase 家族架构图——宿主适配、两个互为平行的 runtime、桥接，以及它们共同消费的契约" width="880">
</p>

> KeelBase 是**一套协议、多个实现与多种宿主**——不是几个互相竞争的产品。契约是唯一真源，
> 各 runtime 与各宿主消费它，**谁也不拥有它**。这里每个仓都是 **Apache-2.0**。

```text
  宿主适配   KeelBase-YuDao
             └ 在成熟企业平台上补治理层（芋道）

  runtime    KeelBase（TypeScript）  ·  KeelBase4J（Java / Spring）
             └ 同一协议的两个载体——互为平行，不是翻译关系

  桥接       KeelBase-java-starter
             └ 把存量 Java/Spring 系统接成受治理的 AI 工具

  契约       keelbase-contract
             └ 线缆 schema + 一致性向量 + 两者共用的版本线
```

| 仓 | 角色 | 技术栈 | 状态 |
|---|---|---|---|
| [`KeelBase`](https://github.com/rain6fish/KeelBase) | 参考实现 · 产品文档 · 演示 | TypeScript · NestJS · Vue · Flutter · Taro | 活跃 |
| [`KeelBase4J`](https://github.com/rain6fish/KeelBase4J) | 同一协议的 Java 载体 | Java 17 · Spring Boot | 活跃 |
| [`KeelBase-java-starter`](https://github.com/rain6fish/KeelBase-java-starter) | 把存量 Java/Spring 系统接成受治理的 AI 工具 | Maven · Gradle | 0.1.x |
| [`KeelBase-YuDao`](https://github.com/rain6fish/KeelBase-YuDao) | 宿主适配——芋道平台上的治理层 | Java · Vue 3 | 活跃 |
| [`keelbase-contract`](https://github.com/rain6fish/keelbase-contract) | 协议本体——schema、向量、版本线 | JSON Schema | v1.x |

**从哪里开始** —— 想看它跑起来：[`KeelBase`](https://github.com/rain6fish/KeelBase)（60 秒
Docker）· 想让存量 Java 系统具备 AI 能力：
[`KeelBase-java-starter`](https://github.com/rain6fish/KeelBase-java-starter) · 你已经在用芋道：
[`KeelBase-YuDao`](https://github.com/rain6fish/KeelBase-YuDao) · 想实现这个协议：
[`keelbase-contract`](https://github.com/rain6fish/keelbase-contract)。

**系列长文** —— 这些决定背后的长文，分「AI 执行治理」「AI 审计证据」两条线：
[OSChina 文集](https://my.oschina.net/u/4166179/blog)。

每个 runtime 对着哪一版契约应答，**只在主仓维护一处**——见
[兼容矩阵](https://github.com/rain6fish/KeelBase/blob/main/README.zh-CN.md#-兼容矩阵)。此处不重复。
<!-- END keelbase-family:zh -->

## 试用征集（0.2.0）

> **0.2.0 已具备生产接入能力**：自动配置条件化可证、JDBC 账本可接宿主 DataSource、可选的 Javadoc 推断，建表改为显式开启（`auto-ddl: true` 恢复旧行为）。接入健康度自检、零样板工具导出、3 个参考项目（CRM / PM / Approval）、本地调试、Boot 2 / Java 8 适配路径。欢迎集成商试用反馈——反馈将决定 1.0 API 冻结。
> **0.2.0 is production-wiring ready** — trial it and open a GitHub Issue with what blocked you or what's missing.

- **5 分钟试用**：clone → `mvn install` → 起 `keelbase-java-example` → `node scripts/verify-java-local.mjs`（接入自检）
- **参考项目**：[CRM](docs/reference-project-crm.zh-CN.md) · [PM](docs/reference-project-pm.md) · [Approval](docs/reference-project-approval.md)
- **完整开发路径**：[开发使用手册](docs/development-guide.md)
- **反馈**：GitHub Issues（https://github.com/rain6fish/KeelBase-java-starter/issues）

## 它能做什么

1. **委托身份**（`DelegationAuthFilter`）：校验 KeelBase 转发请求携带的委托 JWT（HS256 + audience + issuer + 过期），映射到本地用户，注入 `@DelegationUser DelegationPrincipal`。无 Spring Security 也能用；classpath 含 Security 时自动写入 SecurityContext。`secret` 与 `audience` 均为必填——缺任一项启动即失败（fail-fast）。
2. **工具声明 + 导出**（`@KeelbaseTool`）：给 `@RestController` 方法加注解，`GET /keelbase/proxy-tools/export` 导出 `ai_proxy_tools` 配置——写入 KeelBase Settings 即注册为 AI 工具。类型/风险级/参数口径与 KeelBase 生成器对齐；参数提取对齐 Jackson（继承 / @JsonIgnore / record）；audience 单一来源（tools 回退 delegation）。
3. **补偿脚手架**（`KeelBaseCompensationSupport`）：AI 写副作用的撤销补偿端点——委托身份 + 幂等 + 审计，开箱即用。
4. **诊断端点**（`GET /keelbase/status`）：委托配置、解析后的导出 audience、工具计数与配置告警——绝不泄露密钥。

## 文档

| 主题 | 说明 |
|---|---|
| [架构与数据流](docs/architecture.zh-CN.md) | 总览——控制面/数据面、三条数据流（调用/写+撤销/反向）、委托身份桥 |
| [开发使用手册](docs/development-guide.md) | 从零接入的完整开发路径（依赖/配置/工具/委托身份/补偿/自检/测试/发布）|
| [快速开始](docs/quickstart.zh-CN.md) | 10 分钟端到端接入指南 |
| [配置参考](docs/configuration.zh-CN.md) | 全量属性参考 + audience 解析规则 |
| [委托身份与授权](docs/delegated-identity.zh-CN.md) | JWT、验签、用户映射、Spring Security、行级归属 |
| [工具声明与导出](docs/tool-declaration.zh-CN.md) | 注解、参数提取、类型映射、风险级 |
| [补偿与撤销](docs/compensation.zh-CN.md) | 撤销调用契约、幂等、审计、多实例 |
| [客户端与审计上报](docs/client.zh-CN.md) | 委托 token 生命周期（`KeelbaseClient`）+ 审计上报 |
| [参考项目 CRM](docs/reference-project-crm.zh-CN.md) | Integrator Kit 参考项目：传统 Java CRM → AI CRM（Java 侧真实实现） |
| [工具模式](docs/tool-patterns.zh-CN.md) | 配方：分页、筛选、枚举参数、springdoc 描述、类级工具、写+撤销 |
| [Maven 插件](docs/maven-plugin.zh-CN.md) | `keelbase:export/register` 一键导出/注册，配合热更新免重启 |
| [Gradle 使用](docs/gradle-usage.zh-CN.md) | 消费侧 Gradle 指南：依赖、配置、版本 |
| [CI 接入合规模板](docs/ci-integration.zh-CN.md) | GitHub Actions 样板：契约测试 + 自检 + 导出门禁，合规持续守护 |
| [排障与 FAQ](docs/troubleshooting.zh-CN.md) | 错误码、常见坑、核对清单 |
| [生产上线核对清单](docs/production-checklist.zh-CN.md) | 上线硬化、密钥轮换、运维监控 |
| [Boot 2 / Java 8 适配](docs/boot2-java8-adapter.zh-CN.md) | 逐模块支持矩阵、API 代理兜底路径、迁移清单（Java 8 未实测） |

英文版在同目录（`.md`）。

## 快速开始（示例）

```bash
# 1. 启动示例（默认 8081）
cd keelbase-java-example
mvn spring-boot:run

# 2. 诊断接线（委托配置 / 解析后 audience / 工具计数 / 告警）
curl http://localhost:8081/keelbase/status

# 3. 导出 ai_proxy_tools 配置
curl http://localhost:8081/keelbase/proxy-tools/export

# 4. 写入 KeelBase（PUT /settings/ai_proxy_tools，value 为导出 JSON 的字符串）——热更新生效，免重启
```

已发布到 Maven Central——直接加依赖 `cn.com.keelbase:keelbase-spring-boot-starter:0.2.0`（0.1.0–0.2.0 均已上线）。本地开发快照需 `mvn install` 安装一次。发布自动化（`-Prelease` profile + Central Portal token 流程）已配置——见 [docs/release-central.md](docs/release-central.md)。完整步骤见 [docs/quickstart.zh-CN.md](docs/quickstart.zh-CN.md)。

## 配置（application.yml）

```yaml
keelbase:
  delegation:
    secret: ${KEELBASE_DELEGATION_SECRET:}          # 必填，与 KeelBase DELEGATION_SECRET 一致（≥32 字节）
    audience: legacy-crm                             # 必填，须等于 ai_proxy_tools 顶层 audience
    issuer: keelbase                                 # 可选
    paths:                                           # 受保护路径（无 Authorization 头即拒绝）
      - /api/compensation
  tools:
    base-url: http://localhost:8081                  # 服务器根（导出；baseUrl + 完整 path 约定）
    # audience: legacy-crm                           # 可选——缺省回退 delegation.audience
    # export-enabled: true                           # 注册完成后可关闭导出端点
    # status-enabled: true                           # 高安全环境可关闭 /keelbase/status
  compensation:
    ledger-size: 1024                                # 幂等账本 LRU 上限
```

## 注解示例

```java
@RestController
@RequestMapping("/api")
public class FollowupController extends KeelBaseCompensationSupport<Map<String, Object>> {

    @GetMapping("/followups")
    @KeelbaseTool(name = "list_followups", description = "列出跟进任务（读，R1 自动）")
    public List<Map<String, Object>> list() { ... }

    @PostMapping("/followups")
    @KeelbaseTool(name = "create_followup", description = "创建跟进任务（写，R3 需确认）",
                  revokePath = "DELETE /api/compensation/followups/{id}")
    public Map<String, Object> create(@RequestBody FollowupRequest req,
                                      @DelegationUser DelegationPrincipal principal) { ... }

    @DeleteMapping("/compensation/followups/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
        return handleRevoke(request, id, store::get,
                (item, subject) -> item.put("cancelled", true), "compensation.followups.revoke");
    }
}
```

## 模块

| 模块 | 说明 |
|---|---|
| `keelbase-tools-annotation` | `@KeelbaseTool` / `KeelbaseRiskLevel` / `@EnableKeelbaseTools` |
| `keelbase-delegation-filter` | 委托验签过滤器 + 用户映射 SPI + `@DelegationUser` |
| `keelbase-tools-export` | 注解扫描器 + 类型映射 + `/keelbase/proxy-tools/export` |
| `keelbase-compensation` | 补偿脚手架 + 幂等账本 + 审计钩子 |
| `keelbase-client` | `KeelbaseClient`（委托 token 生命周期）+ 治理台审计上报 |
| `keelbase-spring-boot-autoconfigure` | 自动装配（四件套 + 可选 Security 适配） |
| `keelbase-maven-plugin` | Maven 插件：`mvn keelbase:export/register` |
| `keelbase-test-support` | `KeelbaseContractTest` 契约测试基类（自家 CI 内建接入合规验证） |
| `keelbase-java-crm-example` | 参考项目：传统 Java CRM → AI CRM（不发布） |
| `keelbase-spring-boot-starter` | 聚合器（用户只引这个） |
| `keelbase-java-example` | 示例应用（不发布） |

## 契约（与 KeelBase 对齐）

- 委托 JWT：`{sub, oidcSub?, aud, iss:'keelbase', iat, exp}`，HS256，`DELEGATION_SECRET`。
- `ai_proxy_tools`：`{baseUrl, audience, tools:[{name, description, method, path, parameters, queryParams, riskLevel, revokePath}]}`；读 GET=R1 / 写 POST·PUT·PATCH·DELETE=R3；类型 integer/number/boolean/string（复杂结构→string）。
- `baseUrl` 为**服务器根**，工具 `path` 为**完整路径**（如 `/api/followups`）——KeelBase ProxyTool 用 `baseUrl + path` 拼接。
- 撤销：`proxy-revoker` 调补偿端点（带委托身份），须返回 2xx 且幂等。

**这些形状定义出自哪里。** 委托令牌发布在
[`rain6fish/keelbase-contract`](https://github.com/rain6fish/keelbase-contract)——见其
`delegation-token-v1-vector.json` 与 `delegation-token-claims` schema；本 starter 对齐契约 **v1.0.x**。
下面那份 `ai_proxy_tools` 配置形状**不在**该契约里——它是桥接自身的配置面，由本 starter 与运行时之间约定。
若你打算靠契约把依赖的东西全部钉死，这一点值得知道：**它不覆盖这一半**。

## 构建与测试

两套构建系统皆可（Maven 为 CI/发布主构建；Gradle 为平行开发/发布路径，依赖与 pom 一一对应）：

```bash
mvn install          # Maven：编译全部模块 + 跑 JUnit
./gradlew build      # Gradle（含 wrapper）：同一 reactor，全部测试
```

## 端到端验证

```bash
# 前置：KeelBase（localhost:3000）与示例（localhost:8081）已启动
node scripts/verify-java-starter-e2e.mjs --configure   # 导出 + 写入 ai_proxy_tools（热更新生效，免重启）
node scripts/verify-java-starter-e2e.mjs --verify --llm # 完整闭环：确认门控 → 流式批准 → proxy 写回 → 审计 → 撤销 → 补偿
```

## License

Apache-2.0
