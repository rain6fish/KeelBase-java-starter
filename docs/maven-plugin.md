# Maven Plugin — `mvn keelbase:export/register`

> For Maven teams: turns "curl export → copy JSON → PUT settings" into a single command. Combined with KeelBase's hot reload, **the config takes effect without a restart**.
> This plugin is **Maven-only** (Maven plugins cannot expose goals to Gradle); Gradle teams use the HTTP endpoint or the scripts (see [Quick Start](quickstart.md)).

## 1. Goals

| Goal | Command | What it does |
|---|---|---|
| `export` | `mvn keelbase:export` | Calls the running app's `GET /keelbase/proxy-tools/export` → validates (non-empty tools / unique names / legal riskLevel) → writes `target/ai_proxy_tools.json` |
| `register` | `mvn keelbase:register` | On top of export: logs into KeelBase as admin → `PUT /api/v1/settings/ai_proxy_tools` (value = exported JSON string) → hot reload takes effect |

## 2. Usage

```bash
# Prereq: your app is running (default localhost:8081); KeelBase is running (needed for register, default localhost:3000)

# Export only
mvn keelbase:export

# Export + write into KeelBase (defaults admin / Admin@1234)
mvn keelbase:register

# Override parameters (-D or <configuration>)
mvn keelbase:register -Dkeelbase.appUrl=http://localhost:8081 \
                      -Dkeelbase.keelbaseUrl=http://localhost:3000 \
                      -Dkeelbase.username=admin -Dkeelbase.password=Secret@123
```

The artifact `target/ai_proxy_tools.json` can be reviewed / version-controlled.

## 3. Parameters

| Parameter | Default | Description |
|---|---|---|
| `keelbase.appUrl` | `http://localhost:8081` | Running app address (server root; tool paths are full paths) |
| `keelbase.output` | `target/ai_proxy_tools.json` | export output file path |
| `keelbase.keelbaseUrl` | `http://localhost:3000` | KeelBase server root (register) |
| `keelbase.username` / `keelbase.password` | `admin` / `Admin@1234` | Admin login credentials (register; inject via env in production) |

## 4. Full loop (Maven teams)

```bash
mvn keelbase:register   # export → validate → write settings
# KeelBase hot-reloads: no restart, new tools are visible to the next AI conversation
mvn verify              # the export goal is bound to the verify phase (build-time contract check)
```

> Requires Maven 3.9+ / JDK 17+. The plugin exports over HTTP (reuses the app's runtime scanner), so the app must be running.
