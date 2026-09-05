# {{artifactId}}

KeelBase governed AI tools 骨架（由 `scripts/new-keelbase-project.mjs` 生成）——一个最小 Spring Boot 项目，暴露两个受治理的 AI 工具：读 `list_items`（R1 自动）+ 写 `create_item`（R3 需人工确认、可撤销）。

## 前置

- JDK 17+ / Maven
- 一个已启动的 [KeelBase](https://github.com/rain6fish/KeelBase)（默认 `localhost:3000`）

## 启动

```bash
export KEELBASE_DELEGATION_SECRET=<与 KeelBase DELEGATION_SECRET 一致的密钥，≥32字节>
mvn spring-boot:run
```

## 注册工具到 KeelBase（热更新生效，免重启）

```bash
mvn keelbase:register     # 或 Maven 插件：导出 + 写入 Settings
```

## 接线自检

```bash
curl http://localhost:{{port}}/keelbase/status                 # 健康度
curl http://localhost:{{port}}/keelbase/proxy-tools/export     # 导出契约
```

## 合规契约测试（已生成）

```bash
mvn test        # 跑 {{Domain}}ContractTest：导出契约 / 受保护路径 401 / 委托 JWT 2xx 幂等
```

项目已含 `keelbase-test-support` 依赖 + `src/test/java/.../{{Domain}}ContractTest.java`（继承 `KeelbaseContractTest`）——`mvn test` 即守护接入合规；与 ci-template.yml 配合可在 CI 持续跑。

## 下一步

- 替换内存 Store 为你的 Service/DB；在 `{{Domain}}Controller` 加更多 `@KeelbaseTool` 方法
- 生产上线核对：见 KeelBase-java-starter `docs/production-checklist.md`
