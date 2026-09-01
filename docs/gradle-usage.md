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
    implementation 'cn.com.keelbase:keelbase-spring-boot-starter:0.1.4'
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

## 4. Building the starter from source (Gradle)

```bash
git clone https://github.com/rain6fish/KeelBase-java-starter.git
cd KeelBase-java-starter
./gradlew build          # compile all modules + run all JUnit
mvn install              # (Maven) install the SNAPSHOT for local consumption
```

## Versions

| Version | Source |
|---|---|
| `0.1.4` | Maven Central (stable; 0.1.0/0.1.1/0.1.3 live) |
| `0.1.5-SNAPSHOT` | build locally with `./gradlew build` / `mvn install` |

The parent `pom.xml` and `build.gradle` stay in sync (same Spring Boot BOM, same module set), so whichever build you use produces the same artifacts.
