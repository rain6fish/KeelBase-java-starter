# Gradle 使用指南——在 Gradle 项目里接入 starter

starter 双构建（Maven 为 CI/发布主构建；Gradle 是一等公民平行路径）。本页是**消费侧 Gradle 指南**——Spring Boot 3.x 的 Gradle 项目如何拉入 `keelbase-spring-boot-starter` 并自动装配受治理的 AI 工具面。

## 1. 加依赖

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

这一个 `keelbase-spring-boot-starter` 依赖带全：注解、委托验签过滤器、工具导出、补偿、`keelbase-client`。classpath 含 Spring MVC 时全部自动装配。

## 2. 配置 `application.yml`

```yaml
keelbase:
  delegation:
    secret: ${KEELBASE_DELEGATION_SECRET}   # 与 KeelBase DELEGATION_SECRET 一致（≥ 32 字节）
    audience: legacy-crm
    paths:
      - /api/compensation
  tools:
    base-url: http://localhost:8081          # 服务器根；工具 path 为完整路径
```

`secret` 与 `audience` 均为必填——缺任一项应用启动即失败。

## 3. 使用

给 `@RestController` 方法加 `@KeelbaseTool`，启动应用，`GET /keelbase/proxy-tools/export` 导出并在 KeelBase 注册。完整流程见[快速开始](quickstart.zh-CN.md)；常见业务形状配方见[工具模式](tool-patterns.zh-CN.md)。

## 4. 一键注册（Gradle task）

Gradle 团队与 `mvn keelbase:register` 对齐：apply 模板即得两个 task，一条命令完成「导出 → 写 KeelBase Settings → 热更新生效」：

```groovy
apply from: "https://raw.githubusercontent.com/rain6fish/KeelBase-java-starter/main/gradle/keelbase.gradle"
// 或下载本文件到 gradle/keelbase.gradle 后：apply from: "gradle/keelbase.gradle"
```

```bash
./gradlew keelbaseExport     # 导出 ai_proxy_tools 到 target/ai_proxy_tools.json
./gradlew keelbaseRegister   # 导出 + 写入 KeelBase Settings（默认 admin，热更新免重启）
```

覆盖参数：`-PkeelbaseAppUrl=http://localhost:8081 -PkeelbaseKeelbaseUrl=http://localhost:3000 -PkeelbaseUsername=admin -PkeelbasePassword=...`（密码也可用环境变量 `KEELBASE_ADMIN_PASSWORD`）。

> 需要你的项目已依赖 `keelbase-spring-boot-starter`（task 的 classpath 取自 `sourceSets.main.runtimeClasspath`）。

## 5. 从源码用 Gradle 构建 starter

```bash
git clone https://github.com/rain6fish/KeelBase-java-starter.git
cd KeelBase-java-starter
./gradlew build          # 编译全部模块 + 跑全部 JUnit
mvn install              # （Maven）安装 SNAPSHOT 供本地消费
```

## 版本

| 版本 | 来源 |
|---|---|
| `0.1.6` | Maven Central（稳定；0.1.0–0.1.5 也已上线） |
| `0.1.7-SNAPSHOT` | 本地 `./gradlew build` / `mvn install` 构建 |

父 `pom.xml` 与 `build.gradle` 保持同步（同一 Spring Boot BOM、同一模块集），无论用哪套构建产物一致。
