# Release Automation / 发布自动化

> 打 `v*` tag 即自动发布 Maven Central。工作流：`.github/workflows/release.yml` + 父 pom 的 `release` profile（`central-publishing-maven-plugin`，`autoPublish`）。
> Pushing a `v*` tag auto-publishes to Maven Central via `.github/workflows/release.yml` + the parent pom's `release` profile (`central-publishing-maven-plugin`, `autoPublish`).

## 发布动作 / Release action

```bash
git tag v0.1.2 && git push github v0.1.2 && git push origin v0.1.2   # origin = Gitee
```

Workflow 会：checkout → JDK17 → 导入 GPG 私钥 → 写 Central token 到 settings.xml → `mvn -B -Prelease deploy`（source/javadoc/gpg 签名 + central-publishing 自动校验发布）。结果在 GitHub Actions 页查看。

## 一次性配置 / One-time setup（GitHub Secrets）

仓库 → **Settings → Secrets and variables → Actions**，配 5 个：

| Secret | 怎么取 / How to get |
|---|---|
| `GPG_SECRET_KEY` | 本机 `gpg --batch --no-tty --pinentry-mode loopback --passphrase-file <密码文件> --armor --export-secret-keys 7ECAABC1ABDC27F3`，贴**全文**（含 BEGIN/END 行） |
| `GPG_KEY_ID` | `7ECAABC1ABDC27F3` |
| `GPG_PASSPHRASE` | 该 GPG 密钥的 passphrase（纯值，无前缀） |
| `CENTRAL_TOKEN_USERNAME` | central.sonatype.com → 头像 → **Generate User Token** 生成的 username |
| `CENTRAL_TOKEN_PASSWORD` | 同上生成的 password（只显示一次，生成后立即复制） |

> 私钥只进 GitHub secret，勿提交进仓库或贴公开渠道。

## 排障 / Troubleshooting

| 现象 | 处理 |
|---|---|
| Workflow 红在 Import GPG | `GPG_SECRET_KEY` 不是合法 armored 私钥 → 重新导出全文粘贴 |
| `gpg: No secret key` / `No passphrase` | `GPG_KEY_ID`/`GPG_PASSPHRASE` 与私钥不匹配 |
| `401 Unauthorized` 上传 | `CENTRAL_TOKEN_*` 过期/复制缺字 → 重新 Generate User Token |
| 手动发布兜底 | 仍可用 `bash scripts/build-central-upload.sh <版本>` + Portal 网页上传（见 [release-central.md](release-central.md)） |
