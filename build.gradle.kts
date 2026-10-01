import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

plugins {
    java
    jacoco
    checkstyle
    id("org.springframework.boot") version "4.1.1"
}

fun frontendAssetDigest(directory: File): String {
    require(directory.isDirectory) { "Frontend assets are missing: $directory" }
    val digest = MessageDigest.getInstance("SHA-256")
    directory.walkTopDown()
        .filter(File::isFile)
        .sortedBy { it.relativeTo(directory).invariantSeparatorsPath }
        .forEach { asset ->
            digest.update(asset.relativeTo(directory).invariantSeparatorsPath
                .toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(asset.readBytes())
            digest.update(0)
        }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

fun sourceRevision(repository: File): String {
    System.getenv("GITHUB_SHA")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    return try {
        val process = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(repository)
            .redirectErrorStream(true)
            .start()
        val revision = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (process.waitFor() == 0 && revision.isNotEmpty()) revision else "unknown"
    } catch (_: IOException) {
        "unknown"
    }
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

checkstyle {
    toolVersion = "14.3.0"
    configFile = file("config/checkstyle/checkstyle.xml")
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
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testImplementation("io.swagger.parser.v3:swagger-parser:2.1.48")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.withType<Checkstyle> {
    reports {
        xml.required = true
        html.required = true
    }
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

val devDatabaseUp = tasks.register<Exec>("devDatabaseUp") {
    group = "development"
    description = "Start the local PostgreSQL service and wait until it is healthy."
    commandLine("docker", "compose", "up", "--detach", "--wait", "postgres")
}

tasks.register<Exec>("devDatabaseDown") {
    group = "development"
    description = "Stop the local PostgreSQL service without deleting its data volume."
    commandLine("docker", "compose", "down")
}

tasks.register<Exec>("frontendDev") {
    dependsOn(frontendInstall)
    group = "development"
    description = "Run the Vite frontend development server."
    workingDir("frontend")
    commandLine("npm", "run", "dev")
}

tasks.register("devSetup") {
    dependsOn(devDatabaseUp, frontendInstall)
    group = "development"
    description = "Prepare the database and frontend dependencies for local development."
    doLast {
        logger.lifecycle("Development dependencies are ready. Run ./gradlew bootRun and ./gradlew frontendDev in separate terminals.")
    }
}

tasks.bootJar {
    dependsOn(frontendBuild)
    from("frontend/dist") {
        into("BOOT-INF/classes/static")
    }
    doFirst {
        manifest.attributes(
            "Build-Revision" to sourceRevision(rootDir),
            "Frontend-Asset-SHA256" to frontendAssetDigest(file("frontend/dist"))
        )
    }
}

tasks.register<Test>("packagedApplicationTest") {
    dependsOn(tasks.bootJar)
    description = "Start the executable JAR and verify its frontend, health routes, and metadata."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("com.jobtrace.packaging.PackagedApplicationTest")
    }
    inputs.file(tasks.bootJar.flatMap { it.archiveFile })
    systemProperty(
        "jobtrace.packaged.jar",
        tasks.bootJar.flatMap { it.archiveFile }.get().asFile.absolutePath
    )
}
