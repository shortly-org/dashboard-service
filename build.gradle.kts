plugins {
    java
    id("org.springframework.boot") version "4.0.8"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
    id("com.github.spotbugs") version "6.5.12"
    `jvm-test-suite`
    id("com.diffplug.spotless") version "8.10.3"
    id("org.jooq.jooq-codegen-gradle") version "3.20.20"
}

group = "io.shortly"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["jooqVersion"] = "3.20.20"
extra["springdocVersion"] = "3.1.1"
extra["spotbugsVersion"] = "4.10.4"
extra["findsecbugsVersion"] = "1.14.0"
extra["jacocoVersion"] = "0.8.15"
extra["googleJavaFormatVersion"] = "1.36.1"

extra["jooq.version"] = property("jooqVersion") as String

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")

    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:${property("findsecbugsVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jooqCodegen("org.jooq:jooq-meta-extensions")
    jooqCodegen("com.h2database:h2")
}

val migrations = "src/main/resources/db/migration"
val jooqGenerated = "build/generated-sources/jooq"

jooq {
    configuration {
        generator {
            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                properties {
                    property {
                        key = "scripts"
                        value = "$migrations/*.sql"
                    }
                    property {
                        key = "sort"
                        value = "flyway"
                    }
                    property {
                        key = "unqualifiedSchema"
                        value = "none"
                    }
                    property {
                        key = "defaultNameCase"
                        value = "lower"
                    }
                }
                forcedTypes {
                    forcedType {
                        name = "INSTANT"
                        includeTypes = "(?i:timestamp\\s+with\\s+time\\s+zone)"
                    }
                    forcedType {
                        userType = "io.shortly.dashboard.model.LinkStatus"
                        isEnumConverter = true
                        includeExpression = "(?i:.*\\.links\\.status)"
                    }
                }
            }
            target {
                packageName = "io.shortly.dashboard.jooq.generated"
                directory = jooqGenerated
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDir(jooqGenerated)
        }
    }
}

tasks.named("jooqCodegen") {
    inputs.files(fileTree(migrations))
        .withPropertyName("migrations")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.named("compileJava") {
    dependsOn("jooqCodegen")
}

val analysisFailsBuild = providers.gradleProperty("analysisReportOnly").isPresent.not()

spotbugs {
    toolVersion = property("spotbugsVersion") as String
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    excludeFilter = file("config/spotbugs/exclude.xml")
    ignoreFailures = !analysisFailsBuild
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    enabled = name == "spotbugsMain"
    reports.create("html") { required = true }
}

jacoco {
    toolVersion = property("jacocoVersion") as String
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required = true
        html.required = true
    }
    executionData.setFrom(
        fileTree(layout.buildDirectory.dir("jacoco")) { include("test.exec", "integrationTest.exec") }
    )
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude("io/shortly/dashboard/jooq/generated/**") }
        })
    )
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation("org.testcontainers:testcontainers-postgresql")
                implementation("org.testcontainers:testcontainers-kafka")
            }
            targets.all {
                testTask.configure {
                    shouldRunAfter(tasks.named("test"))
                }
            }
        }
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.named("check") {
    dependsOn(tasks.named("integrationTest"))
}

tasks.named("test") {
    finalizedBy("jacocoTestReport")
}

tasks.named("integrationTest") {
    finalizedBy("jacocoTestReport")
}

spotless {
    java {
        target("src/**/*.java")
        targetExclude("build/**")
        googleJavaFormat(property("googleJavaFormatVersion") as String).aosp()
        removeUnusedImports()
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-Xshare:off")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { archiveFileName.set("app.jar") }
