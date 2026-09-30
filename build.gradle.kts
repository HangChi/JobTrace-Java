plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
}

group = "com.jobtrace"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")

    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("com/jobtrace/JobTraceApplication.class")
                exclude("com/jobtrace/shared/security/**")
            }
        })
    )
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("com/jobtrace/JobTraceApplication.class")
                exclude("com/jobtrace/shared/security/**")
            }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

val frontendInstall = tasks.register<Exec>("frontendInstall") {
    workingDir("frontend")
    commandLine("npm", "ci")
    inputs.files("frontend/package.json", "frontend/package-lock.json")
    outputs.dir("frontend/node_modules")
}

val frontendBuild = tasks.register<Exec>("frontendBuild") {
    dependsOn(frontendInstall)
    workingDir("frontend")
    commandLine("npm", "run", "build")
    inputs.dir("frontend/src")
    inputs.files("frontend/index.html", "frontend/vite.config.ts", "frontend/tsconfig.json")
    outputs.dir("frontend/dist")
}

tasks.bootJar {
    dependsOn(frontendBuild)
    from("frontend/dist") {
        into("BOOT-INF/classes/static")
    }
}
