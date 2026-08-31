plugins {
    id("org.springframework.boot") version "4.1.0"
    id("java")
    id("com.diffplug.spotless") version "8.7.0"
}


group = "com.hmg.ipmap"
version = "1.0.0"
description = "IP geolocation mapping service with batch CSV/ZIP import and distributed cache synchronization"

java {
    sourceCompatibility = JavaVersion.toVersion("25")
    targetCompatibility = JavaVersion.toVersion("25")
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

val mapstructVersion = "1.6.3"
val lombokVersion = "1.18.46"
val lombokMapstructBindingVersion = "0.2.0"
val redissonVersion = "4.6.1"
val springdocVersion = "3.0.3"
val commonsLang3Version = "3.20.0"
val commonsCollections4Version = "4.5.0"
val swaggerVersion = "2.2.49"
val ipaddressVersion = "5.6.2"
val hypersistenceVersion = "3.15.3"
val logstashVersion = "9.0"
val assertjVersion = "3.27.7"
val univocityParsersVersion = "2.9.1"
val zeroAllocationHashingVersion = "2026.0"
val tomcatVersion = "11.0.24"

dependencies {

    // ─────────────────────────────────────────────────────────────────
    // implementation
    // ─────────────────────────────────────────────────────────────────

    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))

    // Spring Boot Core Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-batch-jdbc")

    // Redis & Cache
    implementation("org.springframework.boot:spring-boot-starter-data-redis") {
        exclude(group = "io.lettuce", module = "lettuce-core")
    }
    implementation("org.redisson:redisson-spring-boot-starter:$redissonVersion")
    implementation("org.redisson:redisson-spring-cache:$redissonVersion")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Database
    implementation("io.hypersistence:hypersistence-utils-hibernate-70:$hypersistenceVersion")

    // Observability (Monitoring & Logging)
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion")

    // API Documentation (Swagger / OpenAPI)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:$swaggerVersion")

    // Utilities
    implementation("org.apache.commons:commons-lang3:${commonsLang3Version}")
    implementation("org.apache.commons:commons-collections4:${commonsCollections4Version}")
    implementation("com.github.seancfoley:ipaddress:${ipaddressVersion}")
    implementation("com.univocity:univocity-parsers:${univocityParsersVersion}")
    implementation("net.openhft:zero-allocation-hashing:${zeroAllocationHashingVersion}")
    implementation("org.mapstruct:mapstruct:${mapstructVersion}")

    // ─────────────────────────────────────────────────────────────────
    // compileOnly
    // ─────────────────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    compileOnly("org.projectlombok:lombok-mapstruct-binding:$lombokMapstructBindingVersion")

    // ─────────────────────────────────────────────────────────────────
    // annotationProcessor
    // ─────────────────────────────────────────────────────────────────
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:$lombokMapstructBindingVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

    // ─────────────────────────────────────────────────────────────────
    // runtimeOnly
    // ─────────────────────────────────────────────────────────────────
    runtimeOnly("org.postgresql:postgresql")

    // ─────────────────────────────────────────────────────────────────
    // developmentOnly
    // ─────────────────────────────────────────────────────────────────
    developmentOnly("org.springframework.boot:spring-boot-devtools:4.1.0")

    // ─────────────────────────────────────────────────────────────────
    // testImplementation
    // ─────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.junit.platform:junit-platform-launcher")
}

configurations.all {
    resolutionStrategy.force(
        "org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion",
        "org.apache.tomcat.embed:tomcat-embed-el:$tomcatVersion",
        "org.apache.tomcat.embed:tomcat-embed-websocket:$tomcatVersion",
    )
}

tasks.withType<Test> {
    useJUnitPlatform()
    exclude("**/HmgIpmapApiApplicationTests.class")
}


spotless {
    java {
        googleJavaFormat("1.35.0").aosp()
        val filesProp = project.findProperty("spotlessFiles") as String?
        if (!filesProp.isNullOrBlank()) {
            val files = filesProp.split(",").map { it.trim() }
            target(files)
        } else {
            target(
                "src/main/java/**/*.java",
                "src/test/java/**/*.java"
            )
        }
        removeUnusedImports()
    }
}









// ------------------------------------------------------
// Git Hooks
// Hooks are stored in scripts/hooks and copied into
// .git/hooks on every compileJava invocation.
// ------------------------------------------------------
val gitHooksDir   = "${rootProject.projectDir}/../.git/hooks"
val hooksSourceDir = "../scripts/hooks"
val requiredHooks  = listOf("commit-msg", "pre-commit", "prepare-commit-msg")

tasks.register<Copy>("installGitHooks") {
    description = "Install git hooks from scripts/hooks into .git/hooks"
    group       = "git hooks"

    from(hooksSourceDir) {
        include(*requiredHooks.toTypedArray())
    }
    into(gitHooksDir)

    filePermissions {
        unix("rwxr-xr-x")
    }

    doLast {
        println("Git hooks installed successfully:")
        requiredHooks.forEach { println("  - $it") }
    }
}

tasks.register("verifyGitHooks") {
    description = "Verify all required git hooks are installed and executable"
    group       = "git hooks"

    doLast {
        var allOk = true
        requiredHooks.forEach { hook ->
            val hookFile = file("$gitHooksDir/$hook")
            if (hookFile.exists() && hookFile.canExecute()) {
                println("[$hook] installed")
            } else {
                println("[$hook] MISSING or not executable")
                allOk = false
            }
        }
        if (!allOk) {
            throw GradleException(
                "Some git hooks are missing. Run: ./gradlew installGitHooks"
            )
        }
    }
}

tasks.named("compileJava") {
    dependsOn("installGitHooks")
}

tasks.named("test") {
    dependsOn("verifyGitHooks")
}

springBoot {
    //   Auto-generate build info
    buildInfo {
        properties {
            additional.set(mapOf(
                "description" to (project.description ?: "hmg-ipmap-api"),
                "java-version" to System.getProperty("java.version")
            ))
        }
    }
}

// Use filter instead of expand to avoid breaking ${...} in properties files
tasks.withType<ProcessResources> {
    val version = providers.provider { project.version.toString() }
    val name = providers.provider { project.name }

    filesMatching("application.properties") {
        filter { line ->
            line
                .replace("@project_version@", version.get())
                .replace("@project_name@", name.get())
        }
    }
}
