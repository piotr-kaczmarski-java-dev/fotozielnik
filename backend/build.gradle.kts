import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

group = "pl.fotozielnik"
version = "0.0.1-SNAPSHOT"

// Root package of our code. tools/init-project.sh rewrites it together with the source folders.
val basePackage = "pl.fotozielnik"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(libs.springdoc.webmvc.ui)
    implementation("org.jspecify:jspecify")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ---- Static analysis (ADR-0003): Error Prone everywhere, NullAway (JSpecify) as an error in main code.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
        check("NullAway", if (name == "compileJava") CheckSeverity.ERROR else CheckSeverity.OFF)
        option("NullAway:AnnotatedPackages", basePackage)
        option("NullAway:JSpecifyMode", "true")
        // JPA entities and @ConfigurationProperties are initialised by the framework, not the constructor.
        option(
            "NullAway:ExcludedFieldAnnotations",
            "jakarta.persistence.Id,jakarta.persistence.Column,jakarta.persistence.ManyToOne," +
                "jakarta.persistence.OneToMany,jakarta.persistence.Version",
        )
    }
}

// ---- Formatting (ADR-0003). `./gradlew spotlessApply` fixes, `spotlessCheck` verifies.
spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}

springBoot {
    buildInfo {
        excludes = setOf("time") // keeps builds reproducible/cacheable
    }
}

// Integration tests (*IT) need Docker (Testcontainers). `test` runs everything; `unitTest` skips them.
tasks.register<Test>("unitTest") {
    description = "Runs tests that do not need Docker (excludes *IT)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    exclude("**/*IT.class")
}
tasks.named<Test>("test") {
    include("**/*Test.class", "**/*Tests.class", "**/*IT.class")
}
