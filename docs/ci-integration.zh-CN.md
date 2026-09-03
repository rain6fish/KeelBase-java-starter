# CI 接入合规模板 — 接入后合规持续守护

> 把「AI 工具面合规」内建进你的 CI：契约测试 + 本地自检 + 导出门禁，任何改动破坏工具声明（非法名 / 缺 revokePath / 空工具集）都让 CI 红——而不是等运行时「导出缺工具」才发现。

## 三步接入

1. **复制模板**：把仓库根的 [`ci-template.yml`](../ci-template.yml) 复制到你的 `.github/workflows/keelbase.yml`。
2. **加 test-support 依赖**：`pom.xml` test scope 加 `cn.com.keelbase:keelbase-test-support`，并写一个 `ContractComplianceTest extends KeelbaseContractTest`（`@SpringBootTest`——见 [keelbase-java-example 示例](../keelbase-java-example/src/test/java/cn/com/keelbase/example/ContractComplianceTest.java)）。
3. **配启动命令**：`ci-template.yml` 里 integration job 的「启动应用」步骤改成你的启动方式（默认 `mvn spring-boot:run`）；`APP_PORT` 填你的端口；若 `keelbase.delegation.secret/audience` 非默认，给 verify-java-local 传 `KB_SECRET` / `KB_AUDIENCE` env。

## 两个 job 做什么

| Job | 命令 | 守护什么 |
|---|---|---|
| `contract` | `mvn test`（跑 `KeelbaseContractTest`） | 导出契约（工具非空 / audience 一致 / 存在可撤销写工具）、受保护路径无 token 401、委托 JWT 2xx 幂等——**不起端口**，快 |
| `integration` | 起应用 → `verify-java-local` → `keelbase:export` | 本地接入自检（健康度 / 工具契约 / 受保护路径 / 委托验签）+ 导出门禁（非法声明 → 导出失败） |

## 适配点（模板注释已标）

- `APP_PORT`：你的应用端口（`/keelbase/status` 监听）。
- 启动命令：非 `mvn spring-boot:run` 的项目改对应命令（jar / 容器）。
- `KB_SECRET` / `KB_AUDIENCE`：非默认密钥时传给自检脚本。
- 后续想把工具注册也纳入：integration job 加 `keelbase:register`（需 admin 密码，见 [Maven 插件](maven-plugin.zh-CN.md)），配合主仓热更新免重启。
