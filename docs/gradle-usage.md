# Gradle usage — consuming the starter from a Gradle build

The starter is dual-build (Maven canonical for CI/release; Gradle is a first-class parallel path). This page is the **consumer-side Gradle guide** — how a Spring Boot 3.x Gradle project pulls in `keelbase-spring-boot-starter` and auto-configures the governed AI tool surface.

## 1. Add the dependency

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.16'
    id 'io.spring.dependency-management' version '1.1.4'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'cn.com.keelbase:keelbase-spring-boot-starter:0.1.6'
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

The single `keelbase-spring-boot-starter` dependency pulls in: annotation, delegation filter, tools export, compensation, and `keelbase-client`. Everything auto-configures when Spring MVC is on the classpath.

## 2. Configure `application.yml`

```yaml
keelbase:
  delegation:
    secret: ${KEELBASE_DELEGATION_SECRET}   # shared with KeelBase DELEGATION_SECRET (≥ 32 bytes)
    audience: legacy-crm
    paths:
      - /api/compensation
  tools:
    base-url: http://localhost:8081          # server root; tool paths are full paths
```

Both `secret` and `audience` are required — the app fails fast at startup if either is missing.

## 3. Use it

Annotate your `@RestController` methods with `@KeelbaseTool`, start the app, then `GET /keelbase/proxy-tools/export` and register the tools in KeelBase. Full flow in [quickstart](quickstart.md); recipes for common business shapes in [tool-patterns](tool-patterns.md).

## 4. One-command register (Gradle tasks)

Gradle teams get the same experience as `mvn keelbase:register` — apply the template and two tasks appear, one command does "export → write KeelBase Settings → hot reload":

```groovy
apply from: "https://raw.githubusercontent.com/rain6fish/KeelBase-java-starter/main/gradle/keelbase.gradle"
// or download it to gradle/keelbase.gradle and: apply from: "gradle/keelbase.gradle"
```

```bash
./gradlew keelbaseExport     # export ai_proxy_tools to target/ai_proxy_tools.json
./gradlew keelbaseRegister   # export + write KeelBase Settings (default admin, hot reload)
```

Overrides: `-PkeelbaseAppUrl=http://localhost:8081 -PkeelbaseKeelbaseUrl=http://localhost:3000 -PkeelbaseUsername=admin -PkeelbasePassword=...` (or the `KEELBASE_ADMIN_PASSWORD` env var).

> Your project must depend on `keelbase-spring-boot-starter` (the task classpath comes from `sourceSets.main.runtimeClasspath`).

## 5. Building the starter from source (Gradle)

```bash
git clone https://github.com/rain6fish/KeelBase-java-starter.git
cd KeelBase-java-starter
./gradlew build          # compile all modules + run all JUnit
mvn install              # (Maven) install the SNAPSHOT for local consumption
```

## Versions

| Version | Source |
|---|---|
| `0.1.6` | Maven Central (stable; 0.1.0–0.1.5 live) |
| `0.1.7-SNAPSHOT` | build locally with `./gradlew build` / `mvn install` |

The parent `pom.xml` and `build.gradle` stay in sync (same Spring Boot BOM, same module set), so whichever build you use produces the same artifacts.
