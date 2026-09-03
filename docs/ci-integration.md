# CI compliance template — continuous governance on your AI tool surface

> Embed "AI tool-surface compliance" into your CI: contract tests + local self-check + export gate. Any change that breaks a tool declaration (illegal name / missing revokePath / empty tool set) turns CI red — instead of surfacing only at runtime as a missing tool at export.

## Three steps

1. **Copy the template**: copy [`ci-template.yml`](../ci-template.yml) at the repo root to your `.github/workflows/keelbase.yml`.
2. **Add test-support**: add `cn.com.keelbase:keelbase-test-support` as a test dependency and write a `ContractComplianceTest extends KeelbaseContractTest` (`@SpringBootTest` — see the [keelbase-java-example sample](../keelbase-java-example/src/test/java/cn/com/keelbase/example/ContractComplianceTest.java)).
3. **Configure startup**: in `ci-template.yml`, change the "Start app" step in the `integration` job to your launch command (default `mvn spring-boot:run`), set `APP_PORT`, and pass `KB_SECRET` / `KB_AUDIENCE` env to the self-check if your `keelbase.delegation.secret/audience` are not the defaults.

## What the two jobs guard

| Job | Command | Guards |
|---|---|---|
| `contract` | `mvn test` (runs `KeelbaseContractTest`) | Export contract (non-empty tools / audience match / at least one revocable write tool), protected path 401 without token, delegated JWT 2xx idempotent — **no port needed**, fast |
| `integration` | start app → `verify-java-local` → `keelbase:export` | Local self-check (health / tool contract / protected path / delegation) + export gate (invalid declaration fails the export) |

## Adaptation points (marked in the template comments)

- `APP_PORT`: your app's port (`/keelbase/status` listener).
- Startup command: for non-`mvn spring-boot:run` projects, adapt to your command (jar / container).
- `KB_SECRET` / `KB_AUDIENCE`: pass non-default keys to the self-check.
- To also register tools in CI, add `keelbase:register` to the `integration` job (needs the admin password — see [Maven Plugin](maven-plugin.md)), which hot-reloads in KeelBase without a restart.
