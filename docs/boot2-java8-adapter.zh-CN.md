# Boot 2 / Java 8 适配 — 存量 Java 栈接入

不是所有团队都已经升到 Spring Boot 3 + Java 17。本文写清本 Starter 到底要求什么、每个模块用了哪些 API，以及无法升级的宿主该走的兜底路径：KeelBase 的 **API 代理（B 路径）**。

本文使用的状态标记：

- **实测** —— 通过阅读本检出的源码 / 构建文件，或检查 `target/classes` 下的 class 文件确认。
- **推导** —— 由源码级 API 用法推断（JVM 在 Spring 启动前就会拒绝已发布的字节码）。
- **未实测** —— 写本文所用机器只有 JDK 17/25，没有 Java 8/11 JDK，也没有 Spring Boot 2 运行时。本次改动没有跑 Maven/Gradle 构建。

## 1. 结论先说

1. 本 Starter 是 **Spring Boot 3 + Java 17** 产物。所有模块都编译为 class 文件主版本 **61**（Java 17）。把它加到 Boot 2 或 Java 8 应用里，会在类加载阶段失败，根本到不了 Spring 启动。
2. 兜底路径是 **API 代理（B 路径）**：KeelBase 读取存量 REST API 的 OpenAPI 描述，注册受治理的代理工具，再用短期委托 JWT 调用你的端点。**Starter 不在存量 JVM 里运行。** 现有代码与部署保持不变。
3. B 路径**不是零工作量**：你需要提供 OpenAPI 描述；可选地补一个委托 JWT 过滤器和一个补偿端点。第 5 节是迁移清单。
4. 治理分工：KeelBase 负责工具级授权、确认 / 审批、审计与撤销编排；你的端点负责数据级范围收窄和业务事务本身。

## 2. 构建基线（实测）

| 约束 | 取值 | 依据 |
|---|---|---|
| Java source/target | 17 | `pom.xml:32-34` |
| Spring Boot BOM | 3.5.16 | `pom.xml:36`；`build.gradle:6,24` |
| class 文件版本 | 61（Java 17） | 检查每个已构建模块 `target/classes` 下的首个 `*.class` |
| Servlet 命名空间 | `jakarta.*` | 各模块 POM：`keelbase-delegation-filter/pom.xml:25`、`keelbase-tools-export/pom.xml:49`、`keelbase-compensation/pom.xml:33`、`keelbase-spring-boot-autoconfigure/pom.xml:55`、`keelbase-test-support/pom.xml:47` |
| Validation 命名空间 | `jakarta.validation.*` | `keelbase-tools-export/pom.xml:41` |

Maven 构建设置的是 `maven.compiler.source` / `maven.compiler.target` = 17，而不是 `--release 17`。实际产物仍然是 Java 17 字节码；这个区别只在你 fork 后重编译时才有意义。

## 3. A 路径 —— 逐模块支持矩阵

“源码下限”指模块**自身源码**可编译的最低 Java 语言 / API 级别，不考虑本仓的 17 目标；“已发布”指本仓实际产出的构件，它始终是 Java 17。

行号取自写作时的工作树。稳定的引用是**文件 + 符号名**；如果后续重构让行号移动，按符号名搜索即可。

| 模块 | 硬依赖信号（文件:行） | 源码下限 | Boot 下限 | Boot 2 + Java 17 | Java 8 / 11 |
|---|---|---|---|---|---|
| `keelbase-tools-annotation` | 只有注解和枚举；无 Spring、无 `jakarta`；`KeelbaseTool.java`、`KeelbaseRiskLevel.java`、`EnableKeelbaseTools.java` | Java 8 | 无 | 可加载，但单独使用没有任何集成能力 | **不行** —— 已发布 class 文件是 61 |
| `keelbase-delegation-filter` | `jakarta.servlet.*`（`DelegationAuthFilter.java:11-14`）；`record DelegationPrincipal`（`DelegationPrincipal.java:15`）；`String.isBlank()`（`DelegationAuthFilter.java:59,70,83,110`；`DelegationPrincipal.java:24`） | 16 | Boot 3（Jakarta） | **不行** —— `jakarta` + record；Boot 2 用 `javax` | **不行** |
| `keelbase-tools-export` | `jakarta.servlet.*`（`ProxyToolsScanner.java:48-52`）；`jakarta.validation.*`（`RequestBodyFields.java:8-10`）；record（`ProxyToolItem.java:13`、`ProxyToolsConfig.java:8`、`ToolParameter.java:6`、`ProxyToolsScanner.java:83`）；`List.of` / `List.copyOf` / `Set.of`（`ProxyToolsScanner.java:45-46,86,122`；`KeelbaseStatusController.java:80,104,139`）；`instanceof` 模式匹配（`JavadocDocExtractor.java:128,133`）；switch 表达式（`KeelbaseToolsCli.java:28,37`）；`java.net.http`（`ProxyToolsExportClient.java:10-12`）；Spring handler API（`ProxyToolsScanner.java:17,19,95`） | 16 | Boot 3 | **不行** | **不行** |
| `keelbase-compensation` | `jakarta.servlet.http.HttpServletRequest`（`KeelBaseCompensationSupport.java:7`）；`Map.of`（`:57,61,65,69,75`）；引入 delegation-filter 的 record（`:5-6`） | 16（传递） | Boot 3 | **不行** | **不行** |
| `keelbase-client` | record（`GovernancePolicy.java:18,24`；`KeelbaseAuditEvent.java:11`；`KeelbaseTokenIssue.java:14`；`SideEffectStatus.java:23`）；`java.net.http`（`KeelbaseClient.java:19-21`；`KeelbaseAuditReporter.java:12-14`）；`Map.of`（`GovernancePolicy.java:60`）；`String.isBlank()`（`KeelbaseClient.java:69,83,154,215`）；`@ConfigurationProperties` JavaBean（`KeelbaseClientProperties.java:23`）；POM 依赖 delegation-filter（`keelbase-client/pom.xml:18`） | 16 | 仅 Spring Boot 注解；自身源码无 servlet/MVC | **未认证** —— 源码大体框架中立，但构件是 17，且 POM 会带入一个 `jakarta` 模块 | **不行** |
| `keelbase-spring-boot-autoconfigure` | `jakarta.servlet.*`（`SecurityDelegationWriter.java:7-10`）；`@AutoConfiguration`（`CompensationAutoConfiguration.java:36`；`DelegationAutoConfiguration.java:38`）；`AutoConfiguration.imports` 资源；Spring handler API（`ToolsExportAutoConfiguration.java:21,36,38,45`）；`List.of`（`KeelbaseHealthIndicator.java:42,44,45`）；`var`（`ToolsExportValidator.java:29`）；`instanceof` 模式匹配（`SecurityDelegationWriter.java:33`） | 16 | Boot 3 | **不行** | **不行** |
| `keelbase-spring-boot-starter` | 仅聚合 POM | 继承 | Boot 3 | **不行** | **不行** |
| `keelbase-maven-plugin` | 自身源码无 record / `jakarta`；POM 依赖 `keelbase-tools-export`（`keelbase-maven-plugin/pom.xml:34`） | 8（自身源码） | Maven；因依赖而要求 17 运行时 | **不可用** —— 需要 Java 17 运行时 | **不行** |
| `keelbase-test-support` | `jakarta.servlet-api`（`keelbase-test-support/pom.xml:47`）；Spring Boot 测试 | 17 | Boot 3 | **不行** | **不行** |
| 示例：`java`、`crm`、`pm`、`approval` | record（`CrmCustomer.java:6`；`PmProject.java:8`；`PmTask.java:6`；`ApprovalRequest.java:6`；`FollowupRequest.java:8`）；`Stream.toList()`（`CrmController.java:59,65,91`；`CrmInsightsController.java:44,50`）；Boot 3 Web | 17 | Boot 3 | **不行** | **不行** |

几条避免误读的说明：

- `javax.crypto.SecretKey` 出现在 `keelbase-delegation-filter`、`keelbase-client`、`keelbase-test-support`。它是 **JDK** 包（Java 8 就有），不是 Jakarta EE，不能据此判断 Boot 2 兼容。Boot 2 的真正阻塞项是 `jakarta.servlet.*` 和 `jakarta.validation.*`。
- 本仓所有 `@ConfigurationProperties` 类都用 **JavaBean** 形式（getter/setter），没有构造器绑定。这种写法在 Boot 2.7 上同样可用，不是阻塞项。
- `Stream.toList()`（Java 16）出现在 CRM 示例里，生产模块没有用；生产模块用的是 `Collectors.toList()`，但它们有别的阻塞项（record、`jakarta.*`、`java.net.http`）。
- `keelbase-spring-boot-autoconfigure` 使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。Boot 2.7 引入该机制，更早的 Boot 2.x 用 `spring.factories`。机制不是阻塞项 —— 阻塞项是 `jakarta.servlet` 和 Java 17 字节码。

### 各宿主能跑什么

| 宿主 | A 路径（Starter） | B 路径（API 代理） |
|---|---|---|
| Spring Boot 3 + Java 17 | 完整 —— 所有模块 | 可以 |
| Spring Boot 2.7 + Java 17 | 只有 `keelbase-tools-annotation` 能加载；`keelbase-client` 未认证；没有完整能力 | 可以 |
| Spring Boot 2 + Java 11 | 任何模块都加载不了（class 文件主版本 61） | 可以 |
| Spring Boot 2 + Java 8 | 任何模块都加载不了。`keelbase-tools-annotation` 源码在 Java 8 下可编译，但单独使用没有集成能力 | 可以 |
| Spring Boot 3 + Java 8 | 不可能 —— Boot 3 要求 Java 17 | 不适用 |

## 4. B 路径 —— API 代理兜底

### 4.1 它是什么

**B 路径**是 KeelBase AI Bridge 规格里的 API 代理路径。OpenAPI operation 变成受治理的代理工具；执行时 KeelBase 调用你已有的 REST 端点，并注入委托 JWT。

```text
OpenAPI description
  -> ai_proxy_tools (KeelBase Settings)
  -> ProxyToolRegistryService
  -> ProxyTool.execute
  -> HTTP {method} {baseUrl}{path}
       Authorization: Bearer <delegated JWT>
  -> your existing endpoint
```

KeelBase 主仓内的依据：

- `docs/manual/ai-bridge.md` §4（B 路径）与 §5（委托 token）。
- `Server-NestJS/src/ai/proxy/proxy-tool.ts`（运行时代理工具）。
- `Server-NestJS/src/ai/proxy/proxy-revoker.service.ts`（撤销编排）。

存量应用不引入 Starter，也不需要 KeelBase SDK。

### 4.2 存量侧必须提供什么

| 需要 | 是否必须 | 说明 |
|---|---|---|
| 已有 REST 端点 | 是 | 现有 API 保持原样 |
| OpenAPI 3 / Swagger 2 描述 | 是 | Boot 2 上可用 springdoc 或 springfox 生成，也可手写 |
| 委托 JWT 验签 | 建议 | HS256、`aud` / `iss` / `exp`；Java 8 上实现 `javax.servlet.Filter` 或在每个端点内校验 |
| 补偿端点 | 仅在需要撤销时 | 幂等，返回 2xx |
| KeelBase 到存量系统的网络可达 | 是 | `baseUrl` |
| 数据级范围收窄 | 是 | 在端点内按委托身份过滤 |

### 4.3 接入命令（已在主仓核实）

在 **KeelBase 主仓**执行，不是在本 Starter 仓：

```bash
node scripts/keelbase-init.mjs --import-openapi-proxy ./legacy-openapi.yaml \
  --base-url http://legacy-crm:8080/api \
  --audience legacy-crm \
  --out proxy-config.json
```

然后把生成的配置写入 KeelBase：

```text
PUT /api/v1/settings/ai_proxy_tools   (admin token; hot reload, no restart)
```

或者把 JSON 粘贴到管理台「设置」页。

已核实的内容：

- 参数 `--import-openapi-proxy`、`--base-url`、`--audience`、`--out`：`scripts/keelbase-init.mjs:64,76-78`。
- 转换逻辑：`scripts/generator/import-openapi-proxy.mjs`。
- `PUT /api/v1/settings/ai_proxy_tools`：`keelbase-tools-export/src/main/java/cn/com/keelbase/export/ProxyToolsExportClient.java:78-100`。
- 热更新：`Server-NestJS/src/settings/settings.service.ts:26,42`（`ai_proxy_tools` 变更监听器），因此无需重启。
- 可运行 demo：`scripts/demo-external-crm.sh`；`docs/manual/external-crm-demo.md`；`specs/external-crm.openapi.json`。

### 4.4 最小 OpenAPI 片段（仅配置）

```yaml
paths:
  /customers:
    get:
      operationId: listCustomers
      summary: List customers
      responses:
        "200":
          description: OK
  /followups:
    post:
      operationId: createFollowup
      summary: Create a follow-up task
      x-keelbase-risk-level: R3
      x-keelbase-revoke-path: "DELETE /followups/{id}"
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                customerId: { type: integer }
                content: { type: string }
      responses:
        "200":
          description: OK
```

生成器映射（已在 `scripts/generator/import-openapi-proxy.mjs:14,161-178` 核实）：

- `operationId` camelCase → snake_case；缺省 `{method}_{path}`。
- GET → R1；POST/PUT/PATCH/DELETE → R3。
- `x-keelbase-risk-level` 覆盖默认值（R0-R5）。
- `x-keelbase-revoke-path` 变成工具的 `revokePath`。
- 路径模板 `{param}` 原样透传；header 参数跳过。

### 4.5 治理分工

| 事项 | 落在哪一侧 |
|---|---|
| 工具级授权（角色 / enabled） | KeelBase 策略 |
| 风险门控（R1 自动 / R3 确认 / R4 审批 / R5 阻断） | KeelBase |
| 确认与审批状态 | KeelBase |
| 审计（操作审计、决策证据、哈希链） | KeelBase |
| 委托 JWT 签发与注入 | KeelBase（`DelegationTokenService`） |
| 撤销编排 | KeelBase（`ProxyToolRevokerService`） |
| 数据级行 / 租户范围收窄 | **你的端点**，使用委托身份 |
| 业务事务本身 | **你的端点** |

诚实边界：KeelBase 的代理工具携带工具级 `riskLevel` 和 `audience`，**不会**把行级谓词注入你的 SQL。如果 AI 只能看到调用者自己的数据行，你的端点必须按委托主体过滤。这与 Starter 用 `@DelegationUser` + `KeelBaseUserMapper` 帮你实现的是同一项责任；Starter 也不会替你强制执行。

### 4.6 手写过滤器的委托契约

下面的契约就是 Starter 的过滤器所校验的内容（`keelbase-delegation-filter/src/main/java/cn/com/keelbase/delegation/DelegationAuthFilter.java`）。Java 8 实现必须用 `javax.servlet` 和任意支持 HS256 的 JWT 库复现它。

- 请求头：`Authorization: Bearer <token>`。
- 算法：HS256；共享密钥 `DELEGATION_SECRET`（未设置时回退 `JWT_SECRET`；生产应显式配置）。至少 32 字节。
- Claims：`sub` = `oidcSub` 或 `local:<userId>`；有 `oidcSub` 时一并携带；`aud` = 你的系统标识；`iss` = `keelbase`；`exp` = 短期有效（默认 300s，可配 60-3600s）。
- 校验签名、`aud`、可选 `iss` 和过期时间。受保护路径失败时按 fail-closed 处理。

依据：`Server-NestJS/src/auth/delegation-token.service.ts:56-79`；以及上面的 Starter 过滤器。

**注意**：KeelBase 主仓的独立 Java 参考实现（`docs/integrator-kit/java-compensation-example.md`）使用了 `Map.of`（Java 9+）。在 Java 8 上要换成 `Collections.singletonMap` / `HashMap`。本文没有在 Java 8 上编译或运行该示例。

### 4.7 撤销契约

- `revokePath` 格式：`DELETE /path/{id}` 或 `POST /path/{id}/cancel`；方法可省略，缺省 `POST`。
- 占位符 `{...}` 会替换为副作用的 `resultId`。
- KeelBase 调用补偿端点时注入委托 JWT。
- 端点应幂等并返回 2xx；非 2xx 会被上层视为撤销失败。
- `revokeClass` 推导：写 + `revokePath` → `governed_external`；读，或写但没有 `revokePath` → `none`。
- 没有 `revokePath` 时，KeelBase 会明确返回「外部副作用撤销需 Java 端补偿」，不会假装撤销成功。

依据：`Server-NestJS/src/ai/proxy/proxy-revoker.service.ts`；`Server-NestJS/src/ai/proxy/proxy-tool.ts`（`revokeClass` 推导）。

### 4.8 B 路径拿不到什么

| Starter 能力 | B 路径上 |
|---|---|
| `@KeelbaseTool` 声明 | 自己写 OpenAPI 描述 |
| `GET /keelbase/proxy-tools/export` | 不可用；从 OpenAPI 生成配置 |
| `GET /keelbase/status` 接入诊断 | 不可用；用 KeelBase 侧诊断或自己的健康检查 |
| `DelegationAuthFilter` + `@DelegationUser` + `KeelBaseUserMapper` | 手写 `javax.servlet.Filter`（Java 8）或在每个端点校验 |
| `SecurityDelegationWriter`（Spring Security 桥） | 手写 |
| `KeelBaseCompensationSupport`（幂等脚手架） | 手写补偿端点 |
| `KeelbaseClient`（出站 token 签发、审计上报） | 自己调用 KeelBase HTTP API（`/api/v1/auth/delegation-token`、审计端点） |
| `KeelbaseContractTest` | 没有契约测试脚手架；用自己的测试 |
| 自动配置与启动期 fail-fast 校验 | 无自动配置；自行校验配置 |
| 零样板 Javadoc 推断（`infer = true`） | 没有；描述来自 OpenAPI `summary` / `description` |

仍然保留：R1 自动 / R3 确认 / R4 审批 / R5 阻断、审计链、委托身份、通过 `revokePath` 撤销、KeelBase 侧的工具清单。

## 5. 迁移清单：Starter → B 路径

1. **不要**把 `keelbase-spring-boot-starter` 加到存量应用。保持现有 Boot 2 / Java 8 部署。
2. 盘点希望 AI 使用的端点。宁可小而明确，不要「全部暴露」。
3. 编写或导出 OpenAPI 3 / Swagger 2 描述。Boot 2 上可用 springdoc 或 springfox 生成；手写 spec 也可以。
4. 为每个 operation 设置 `operationId`、`summary`、path/query/body 参数和预期风险级。写操作默认 R3；需要 R4/R5 时用 `x-keelbase-risk-level`。
5. 对可撤销的写操作，加 `x-keelbase-revoke-path`，并实现补偿端点（幂等、2xx）。
6. 用 `node scripts/keelbase-init.mjs --import-openapi-proxy ...` 生成代理配置。
7. 应用前先看生成器的 `skipped`、`riskBases`、`available` 输出。
8. 把顶层 `audience` 设为存量系统标识；两侧配置相同的 `DELEGATION_SECRET`。
9. 在存量侧实现委托 JWT 验签。Java 8 上用 `javax.servlet.Filter` + 一个支持 HS256 的 JWT 库，校验 `aud`、`iss`、`exp`。
10. 在每个端点内按委托身份执行数据级范围收窄。
11. 用 `PUT /api/v1/settings/ai_proxy_tools`（热更新）或管理台「设置」页应用配置。
12. 端到端验收：读自动执行（R1）、写要求确认（R3）、审计记录可见、撤销会调用补偿端点、他人数据被拒（403）。
13. 去掉对 Starter 专属端点的依赖：存量侧没有 `/keelbase/status`，也没有 `/keelbase/proxy-tools/export`。

## 6. 范围、证据与未实测项

### 实测

- 构建属性：`pom.xml:32-36`；`build.gradle:6,24`。
- 每个已构建模块的 class 文件版本都是 61（检查 `target/classes/**/*.class`）。
- 逐模块 API 用法：第 3 节里的文件:行引用。
- 主仓命令与路径：`scripts/keelbase-init.mjs`、`scripts/generator/import-openapi-proxy.mjs`、`scripts/demo-external-crm.sh`、`specs/external-crm.openapi.json`、`docs/manual/external-crm-demo.md`、`docs/integrator-kit/java-compensation-example.md`。
- 主仓端点与配置键：`PUT /api/v1/settings/ai_proxy_tools`、`POST /api/v1/auth/delegation-token`、`ai_proxy_tools`、`x-keelbase-risk-level`、`x-keelbase-revoke-path`。

### 未实测

- 写本文所用机器没有 Java 8 或 Java 11 JDK（只有 JDK 17/25）。
- 没有 Spring Boot 2 运行时。
- 本次改动没有跑 Maven/Gradle 构建；该检出与另一个正在构建的会话共用。本次改动只涉及文档。
- 因此兼容性矩阵是**静态依赖分析**，不是运行时认证。如果需要保证，先在 CI 中用 Boot 2 / Java 8 样例应用验证 B 路径，再依赖它。

### 本次修订纠正的内容

上一版（0.1.8）本文中有以下与代码不符的说法：

1. 「`keelbase-delegation-filter` 只依赖 Spring Web，Boot 2 可编」。它引入了 `jakarta.servlet.*` 并声明了 `record`，无法在 Boot 2 / Java 8 上运行。Java 8 路径是手写 `javax.servlet.Filter`，不是这个模块。
2. 「写完 `ai_proxy_tools` 后重启 KeelBase」。设置服务会触发变更监听器，配置热更新，无需重启。
3. 「参考项目是 `external-crm-demo`」。这不是仓库路径。真实入口是 `scripts/demo-external-crm.sh`、`docs/manual/external-crm-demo.md`、`specs/external-crm.openapi.json`。
4. 「B 路径零代码」。说法过头。存量侧仍需 OpenAPI 描述，可选地还要 JWT 过滤器和补偿端点。
5. 「扫描器要求 Spring 6.1 / Boot 3.2+」。代码调用 `getPathPatternsCondition()`，并回退到 `getPatternsCondition()`；硬证据是 Spring 6 的 `RequestMappingHandlerMapping` + Jakarta + Java 17。6.0/6.1 的精确边界没有实测。

## 7. 参见

Starter 文档：

- [开发使用手册](development-guide.md)
- [委托身份与授权](delegated-identity.zh-CN.md)
- [补偿与撤销](compensation.zh-CN.md)
- [排障与 FAQ](troubleshooting.zh-CN.md)

KeelBase 主仓：

- [AI Bridge 规格](https://github.com/rain6fish/KeelBase/blob/main/docs/manual/ai-bridge.md)
- [独立 Java 补偿示例](https://github.com/rain6fish/KeelBase/blob/main/docs/integrator-kit/java-compensation-example.md)
- [外部 CRM demo](https://github.com/rain6fish/KeelBase/blob/main/docs/manual/external-crm-demo.md)
