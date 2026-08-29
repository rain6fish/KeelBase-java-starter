# Publish to Maven Central / Maven Central 发布指南

> 一次性把 `KeelBase-java-starter` 发布到 Maven Central（`cn.com.keelbase:*`）。需要 **OSSRH 账号 + GPG 密钥**，本指南就绪后照做即可。
> One-time guide to publish `KeelBase-java-starter` to Maven Central (`cn.com.keelbase:*`). Requires an **OSSRH account + GPG key** — follow this once the account is ready.

## 前置 / Prerequisites

1. **OSSRH 账号**：在 [Sonatype JIRA](https://issues.sonatype.org) 注册，提 ticket 申请 `cn.com.keelbase` 命名空间（附 GitHub 仓库 URL 验证归属）。批准后获得 **OSSRH 用户名/密码**（Central Portal 旧流程：`s01.oss.sonatype.org` 上传，新流程 `central.sonatype.com`）。
2. **GPG 密钥**：生成并上传公钥（Maven Central 要求所有构件 GPG 签名）。
3. **域名/仓库验证**：`cn.com.keelbase` 需验证你持有该命名空间（GitHub 仓库 `rain6fish/KeelBase-java-starter` 作为归属证据）。

## 一次性配置 / One-time Setup

### 1. `~/.m2/settings.xml`（OSSRH 凭据 + GPG passphrase）

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>YOUR_OSSRH_USERNAME</username>
      <password>YOUR_OSSRH_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>ossrh</id>
      <properties>
        <gpg.keyname>YOUR_GPG_KEY_ID</gpg.keyname>
        <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>ossrh</activeProfile>
  </activeProfiles>
</settings>
```

### 2. GPG 公钥上传

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_GPG_KEY_ID
gpg --keyserver pgp.mit.edu --send-keys YOUR_GPG_KEY_ID
```

### 3. 域名归属（`cn.com.keelbase`）

按 OSSRH ticket 指引，在 `https://github.com/rain6fish/KeelBase-java-starter` 创建验证文件/记录，证明你持有该命名空间。

## 发布 / Publish

仓库已内置发布配置（父 `pom.xml` 的 `-Prelease` profile：source + javadoc + GPG 签名 + Central 所需元数据 `<url>`/`<scm>`/`<licenses>`）。

### 首次发布（snapshot → release）

```bash
# 1. 确认版本号（示例 0.1.0，去掉 -SNAPSHOT）
mvn versions:set -DnewVersion=0.1.0

# 2. 全模块构建 + 测试
mvn clean install

# 3. 发布到 OSSRH 暂存仓库（-Prelease 触发签名 + source/javadoc）
mvn deploy -Prelease -DskipTests
```

### 若用 Central Portal（s01.oss.sonatype.org 新流程）

改用 `central.sonatype.com` 的发布 API（`central` server id + publish 步骤），或在 `-Prelease` 上加 `-DaltDeploymentRepository` 指向你的 staging repo。

### 验证 / Verify

```bash
# 暂存仓库检查（Staging → Close → Release 流程在 OSSRH UI）
# 中央同步后（几小时）：
curl https://repo1.maven.org/maven2/cn/com/keelbase/keelbase-spring-boot-starter/0.1.0/
# 或
https://central.sonatype.com/artifact/cn.com.keelbase/keelbase-spring-boot-starter
```

## 版本维护 / Versioning

- **快照**：`0.1.1-SNAPSHOT`（开发中，不发布 Central，本地 `mvn install` 用）
- **发布**：`0.1.1`（去 `-SNAPSHOT` + `mvn deploy -Prelease`）
- **版本号策略**：遵循项目 [version-strategy](https://github.com/rain6fish/KeelBase)（1.0.x 增量维护）

## 常见问题 / Troubleshooting

| 问题 | 处理 |
|---|---|
| `[ERROR] ... No public key: Key with id ...` | 公钥未上传 → `gpg --keyserver ... --send-keys` |
| `[ERROR] Failed to deploy artifacts: 401` | `~/.m2/settings.xml` ossrh 凭据错 |
| `[ERROR] ... signature validation` | GPG 密钥与 `gpg.keyname` 不一致 |
| Central 拒绝 `cn.com.keelbase` 未验证 | 先完成命名空间归属验证 |
