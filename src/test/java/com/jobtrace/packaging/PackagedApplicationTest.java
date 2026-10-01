package com.jobtrace.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.jar.JarFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "jobtrace.packaged.jar", matches = ".+")
class PackagedApplicationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static Process application;
    private static Path applicationOutput;
    private static URI baseUri;

    @BeforeAll
    static void startPackagedApplication() throws Exception {
        Path jar = Path.of(System.getProperty("jobtrace.packaged.jar")).toAbsolutePath();
        int port = availablePort();
        applicationOutput = Files.createTempFile("jobtrace-packaged-", ".log");
        application = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-jar",
                        jar.toString(),
                        "--server.port=" + port,
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/jobtrace",
                        "--spring.datasource.hikari.connection-timeout=500")
                .redirectErrorStream(true)
                .redirectOutput(applicationOutput.toFile())
                .start();
        baseUri = URI.create("http://127.0.0.1:" + port);
        awaitApplication();
    }

    @AfterAll
    static void stopPackagedApplication() throws InterruptedException, IOException {
        if (application != null) {
            application.destroy();
            if (!application.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                application.destroyForcibly();
            }
        }
        if (applicationOutput != null) {
            Files.deleteIfExists(applicationOutput);
        }
    }

    @Test
    void executableJarServesFrontendAndHealthRoutesWithBuildMetadata() throws Exception {
        HttpResponse<String> shell = get("/");
        HttpResponse<String> liveness = get("/api/health/live");
        HttpResponse<String> readiness = get("/api/health/ready");

        assertThat(shell.statusCode()).isEqualTo(200);
        assertThat(shell.body()).contains("<div id=\"root\"></div>");
        assertThat(liveness.statusCode()).isEqualTo(200);
        assertThat(liveness.body()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(readiness.statusCode()).isEqualTo(503);
        assertThat(readiness.body()).isEqualTo("{\"status\":\"error\"}");

        try (JarFile jar = new JarFile(System.getProperty("jobtrace.packaged.jar"))) {
            var attributes = jar.getManifest().getMainAttributes();
            assertThat(attributes.getValue("Build-Revision")).isNotBlank();
            assertThat(attributes.getValue("Frontend-Asset-SHA256"))
                    .matches("[0-9a-f]{64}");
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void awaitApplication() throws Exception {
        Instant deadline = Instant.now().plusSeconds(30);
        Exception lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            if (!application.isAlive()) {
                throw new IllegalStateException("Packaged application exited:\n" + Files.readString(applicationOutput));
            }
            try {
                if (get("/api/health/live").statusCode() == 200) {
                    return;
                }
            } catch (IOException | InterruptedException exception) {
                lastFailure = exception;
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Packaged application did not start:\n"
                + Files.readString(applicationOutput), lastFailure);
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
