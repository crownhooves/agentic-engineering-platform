package com.example.agentic.tools;

import com.example.agentic.agents.FileBundle.GeneratedFile;
import com.example.agentic.config.WorkspaceProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic runtime verification for a generated application workspace.
 *
 * <p>The verifier:
 *
 * <ol>
 *   <li>Injects a temporary runtime launcher.</li>
 *   <li>Packages the generated application as an executable JAR.</li>
 *   <li>Locates and records the produced executable artifact.</li>
 *   <li>Starts the actual packaged JAR in a separate JVM.</li>
 *   <li>Waits for application readiness.</li>
 *   <li>Exercises the URL-shortener HTTP API.</li>
 *   <li>Returns structured verification results.</li>
 *   <li>Always terminates the child process.</li>
 *   <li>Removes the temporary launcher source.</li>
 * </ol>
 *
 * <p>The packaged JAR is deliberately retained in the run workspace so
 * that it can subsequently be exposed as the downloadable engineering
 * artifact.
 */
@Component
public final class RuntimeVerificationService {

    private static final String LAUNCHER_PATH =
            "src/main/java/com/example/generated/urlshortener/RuntimeLauncher.java";

    private static final Pattern READY_PATTERN =
            Pattern.compile("RUNTIME_READY:(\\d+)");

    private static final Duration HTTP_TIMEOUT =
            Duration.ofSeconds(5);

    private final WorkspaceManager workspaceManager;
    private final WorkspaceProperties properties;
    private final ExecutableArtifactService executableArtifactService;

    public RuntimeVerificationService(
            WorkspaceManager workspaceManager,
            WorkspaceProperties properties,
            ExecutableArtifactService executableArtifactService) {

        this.workspaceManager = workspaceManager;
        this.properties = properties;
        this.executableArtifactService = executableArtifactService;
    }

    /**
     * Packages and verifies the generated application.
     *
     * <p>The executable JAR produced during this operation is the same
     * artifact that remains available for subsequent download.
     *
     * @param runId run being verified
     * @return structured runtime verification result
     */
    public RuntimeVerificationResult verify(String runId) {

        long started = System.nanoTime();

        Path workspace =
                workspaceManager.workspace(runId);

        List<RuntimeVerificationResult.CheckResult> checks =
                new ArrayList<>();

        Process process = null;

        int port = -1;

        try {

            if (!Files.isDirectory(workspace)) {

                return failed(
                        port,
                        started,
                        checks,
                        "Generated workspace does not exist: "
                                + workspace);
            }

            /*
             * The launcher is temporary verification infrastructure.
             *
             * It must exist while Maven packages the project because
             * RuntimeLauncher is configured as the executable JAR's
             * Main-Class.
             */
            workspaceManager.materializeFiles(
                    runId,
                    List.of(
                            new GeneratedFile(
                                    LAUNCHER_PATH,
                                    launcherSource(runId))));

            /*
             * Step 1:
             *
             * Package the generated application as an executable JAR.
             *
             * ExecutableArtifactService delegates the Maven package
             * operation to CompileAndTestRunner and then identifies
             * the resulting JAR and calculates its SHA-256.
             */
            ExecutableArtifactService.ExecutableArtifact executable =
                    executableArtifactService.packageExecutable(runId);

            if (executable == null
                    || executable.path() == null
                    || !Files.isRegularFile(executable.path())) {

                checks.add(
                        check(
                                "package",
                                false,
                                0,
                                -1,
                                "Maven did not produce an executable JAR"));

                return failed(
                        port,
                        started,
                        checks,
                        "Executable JAR was not produced");
            }

            checks.add(
                    check(
                            "package",
                            true,
                            0,
                            0,
                            "Executable JAR packaged successfully: "
                                    + executable.fileName()
                                    + " ("
                                    + executable.size()
                                    + " bytes, SHA-256="
                                    + executable.sha256()
                                    + ")"));

            /*
             * Step 2:
             *
             * Choose an ephemeral TCP port.
             */
            port = findAvailablePort();

            /*
             * Step 3:
             *
             * Start THE ACTUAL PACKAGED JAR.
             *
             * This is the important Batch 7 behavior.
             *
             * We deliberately do not run:
             *
             *     java -cp target/classes ...
             *
             * Instead, the exact executable artifact that will later
             * be offered for download is executed with:
             *
             *     java -jar <artifact>
             */
            Path executablePath =
                    executable.path()
                            .toAbsolutePath()
                            .normalize();

            process =
                    new ProcessBuilder(
                            javaExecutable(),
                            "-jar",
                            executablePath.toString(),
                            Integer.toString(port))
                            .directory(workspace.toFile())
                            .redirectErrorStream(true)
                            .start();

            ProcessOutputReader outputReader =
                    new ProcessOutputReader(
                            process.getInputStream(),
                            properties.maxOutputChars());

            Thread reader =
                    new Thread(
                            outputReader,
                            "runtime-verification-output-reader");

            reader.setDaemon(true);
            reader.start();

            /*
             * Step 4:
             *
             * Wait for deterministic application readiness.
             */
            boolean ready =
                    waitForReady(
                            process,
                            outputReader,
                            properties.commandTimeout());

            if (!ready) {

                checks.add(
                        check(
                                "startup",
                                false,
                                0,
                                -1,
                                truncate(
                                        outputReader.output())));

                return failed(
                        port,
                        started,
                        checks,
                        "Generated executable JAR did not become ready. "
                                + truncate(outputReader.output()));
            }

            /*
             * Make sure the application started on the port that
             * was supplied to the executable JAR.
             */
            if (outputReader.readyPort() != port) {

                checks.add(
                        check(
                                "startup",
                                false,
                                0,
                                -1,
                                "Expected runtime port "
                                        + port
                                        + " but executable reported "
                                        + outputReader.readyPort()));

                return failed(
                        port,
                        started,
                        checks,
                        "Executable JAR started on an unexpected port");
            }

            checks.add(
                    check(
                            "startup",
                            true,
                            0,
                            0,
                            "Executable JAR started on port "
                                    + outputReader.readyPort()));

            /*
             * Step 5:
             *
             * Wait until the HTTP server is actually reachable.
             */
            waitForHttpReady(port);

            HttpClient client =
                    HttpClient.newBuilder()
                            .connectTimeout(HTTP_TIMEOUT)
                            .followRedirects(
                                    HttpClient.Redirect.NEVER)
                            .build();

            /*
             * Step 6:
             *
             * POST /api/links
             */
            HttpResponse<String> createResponse =
                    createLink(
                            client,
                            port);

            boolean createPassed =
                    createResponse.statusCode() == 201
                            && containsJsonField(
                            createResponse.body(),
                            "code")
                            && containsJsonField(
                            createResponse.body(),
                            "url");

            checks.add(
                    check(
                            "create-link",
                            createPassed,
                            201,
                            createResponse.statusCode(),
                            truncate(
                                    createResponse.body())));

            if (!createPassed) {

                return failed(
                        port,
                        started,
                        checks,
                        "POST /api/links verification failed: "
                                + truncate(
                                createResponse.body()));
            }

            String code =
                    extractJsonString(
                            createResponse.body(),
                            "code");

            if (code == null || code.isBlank()) {

                checks.add(
                        check(
                                "create-link-code",
                                false,
                                201,
                                createResponse.statusCode(),
                                "Response did not contain a usable "
                                        + "short code"));

                return failed(
                        port,
                        started,
                        checks,
                        "Create-link response did not contain a usable code");
            }

            /*
             * Step 7:
             *
             * GET /{code}
             */
            HttpResponse<String> redirectResponse =
                    getRedirect(
                            client,
                            port,
                            code);

            String location =
                    redirectResponse.headers()
                            .firstValue("Location")
                            .orElse("");

            boolean redirectPassed =
                    redirectResponse.statusCode() == 302
                            && "https://example.com".equals(location);

            checks.add(
                    check(
                            "redirect",
                            redirectPassed,
                            302,
                            redirectResponse.statusCode(),
                            "Location=" + location));

            if (!redirectPassed) {

                return failed(
                        port,
                        started,
                        checks,
                        "GET /{code} redirect verification failed");
            }

            /*
             * Step 8:
             *
             * GET /api/links/{code}/stats
             */
            HttpResponse<String> statsResponse =
                    getStats(
                            client,
                            port,
                            code);

            boolean statsPassed =
                    statsResponse.statusCode() == 200
                            && containsJsonField(
                            statsResponse.body(),
                            "totalClicks")
                            && containsJsonField(
                            statsResponse.body(),
                            "lastClick");

            checks.add(
                    check(
                            "analytics",
                            statsPassed,
                            200,
                            statsResponse.statusCode(),
                            truncate(
                                    statsResponse.body())));

            if (!statsPassed) {

                return failed(
                        port,
                        started,
                        checks,
                        "GET /api/links/{code}/stats verification failed: "
                                + truncate(
                                statsResponse.body()));
            }

            /*
             * Step 9:
             *
             * Verify unknown URL handling.
             */
            HttpResponse<String> notFoundResponse =
                    getUnknownLink(
                            client,
                            port);

            boolean notFoundPassed =
                    notFoundResponse.statusCode() == 404;

            checks.add(
                    check(
                            "not-found",
                            notFoundPassed,
                            404,
                            notFoundResponse.statusCode(),
                            truncate(
                                    notFoundResponse.body())));

            if (!notFoundPassed) {

                return failed(
                        port,
                        started,
                        checks,
                        "Unknown URL verification failed");
            }

            /*
             * All runtime checks passed.
             *
             * The executable JAR remains in target/ and can now be
             * associated with the completed engineering run.
             */
            return RuntimeVerificationResult.passed(
                    port,
                    elapsed(started),
                    checks);

        } catch (Exception e) {

            checks.add(
                    check(
                            "runtime-exception",
                            false,
                            0,
                            -1,
                            e.getClass().getSimpleName()
                                    + ": "
                                    + e.getMessage()));

            return failed(
                    port,
                    started,
                    checks,
                    "Runtime verification failed: "
                            + e.getMessage());

        } finally {

            /*
             * Always terminate the generated application.
             */
            stopProcess(process);

            /*
             * Remove only the temporary launcher source.
             *
             * IMPORTANT:
             *
             * Do NOT delete target/*.jar here.
             *
             * The executable JAR is the Batch 7 artifact that must
             * remain available for download.
             */
            deleteTemporaryLauncher(workspace);
        }
    }

    private RuntimeVerificationResult failed(
            int port,
            long started,
            List<RuntimeVerificationResult.CheckResult> checks,
            String reason) {

        return RuntimeVerificationResult.failed(
                port,
                elapsed(started),
                checks,
                reason);
    }

    private static Duration elapsed(
            long started) {

        return Duration.ofNanos(
                System.nanoTime() - started);
    }

    private static RuntimeVerificationResult.CheckResult check(
            String name,
            boolean passed,
            int expectedStatus,
            int actualStatus,
            String detail) {

        return new RuntimeVerificationResult.CheckResult(
                name,
                passed,
                expectedStatus,
                actualStatus,
                detail == null
                        ? ""
                        : detail);
    }

    private static HttpResponse<String> createLink(
            HttpClient client,
            int port)
            throws IOException, InterruptedException {

        String body =
                """
                {"url":"https://example.com","alias":"runtime-check"}
                """;

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + "/api/links"))
                        .timeout(HTTP_TIMEOUT)
                        .header(
                                "Content-Type",
                                "application/json")
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(body))
                        .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> getRedirect(
            HttpClient client,
            int port,
            String code)
            throws IOException, InterruptedException {

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + "/"
                                                + code))
                        .timeout(HTTP_TIMEOUT)
                        .GET()
                        .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> getStats(
            HttpClient client,
            int port,
            String code)
            throws IOException, InterruptedException {

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + "/api/links/"
                                                + code
                                                + "/stats"))
                        .timeout(HTTP_TIMEOUT)
                        .GET()
                        .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> getUnknownLink(
            HttpClient client,
            int port)
            throws IOException, InterruptedException {

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + "/does-not-exist"))
                        .timeout(HTTP_TIMEOUT)
                        .GET()
                        .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString());
    }

    private static boolean containsJsonField(
            String body,
            String field) {

        if (body == null) {
            return false;
        }

        return body.contains(
                "\"" + field + "\"");
    }

    private static String extractJsonString(
            String body,
            String field) {

        if (body == null) {
            return null;
        }

        String marker =
                "\"" + field + "\"";

        int fieldIndex =
                body.indexOf(marker);

        if (fieldIndex < 0) {
            return null;
        }

        int colon =
                body.indexOf(
                        ':',
                        fieldIndex + marker.length());

        if (colon < 0) {
            return null;
        }

        int firstQuote =
                body.indexOf(
                        '"',
                        colon + 1);

        if (firstQuote < 0) {
            return null;
        }

        int secondQuote =
                body.indexOf(
                        '"',
                        firstQuote + 1);

        if (secondQuote < 0) {
            return null;
        }

        return body.substring(
                firstQuote + 1,
                secondQuote);
    }

    private static String truncate(
            String value) {

        if (value == null) {
            return "";
        }

        int maximum = 1_500;

        if (value.length() <= maximum) {
            return value;
        }

        return value.substring(
                0,
                maximum)
                + "...";
    }

    private static boolean waitForReady(
            Process process,
            ProcessOutputReader output,
            Duration timeout)
            throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + timeout.toNanos();

        while (System.nanoTime() < deadline) {

            if (output.readyPort() > 0) {
                return true;
            }

            if (!process.isAlive()) {
                return false;
            }

            Thread.sleep(100);
        }

        return false;
    }

    private static void waitForHttpReady(
            int port)
            throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + Duration.ofSeconds(5)
                        .toNanos();

        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofMillis(250))
                        .followRedirects(
                                HttpClient.Redirect.NEVER)
                        .build();

        while (System.nanoTime() < deadline) {

            try {

                HttpRequest request =
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/does-not-exist"))
                                .timeout(
                                        Duration.ofMillis(500))
                                .GET()
                                .build();

                HttpResponse<Void> response =
                        client.send(
                                request,
                                HttpResponse.BodyHandlers
                                        .discarding());

                if (response.statusCode() > 0) {
                    return;
                }

            } catch (IOException ignored) {

                /*
                 * Server has not started listening yet.
                 */
            }

            Thread.sleep(100);
        }

        throw new IllegalStateException(
                "Runtime server did not become reachable on port "
                        + port);
    }

    private static int findAvailablePort()
            throws IOException {

        try (java.net.ServerSocket socket =
                     new java.net.ServerSocket(0)) {

            socket.setReuseAddress(true);

            return socket.getLocalPort();
        }
    }

    private static void stopProcess(
            Process process) {

        if (process == null
                || !process.isAlive()) {

            return;
        }

        process.destroy();

        try {

            if (!process.waitFor(
                    2,
                    TimeUnit.SECONDS)) {

                process.destroyForcibly();

                process.waitFor(
                        2,
                        TimeUnit.SECONDS);
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            process.destroyForcibly();
        }
    }

    private static void deleteTemporaryLauncher(
            Path workspace) {

        try {

            Files.deleteIfExists(
                    workspace.resolve(
                            LAUNCHER_PATH));

        } catch (IOException ignored) {

            /*
             * Cleanup is best effort. The verification result
             * must not be replaced by a cleanup failure.
             */
        }
    }

    private static String javaExecutable() {

        Path javaHome =
                Path.of(
                        System.getProperty(
                                "java.home"));

        Path java =
                javaHome
                        .resolve("bin")
                        .resolve("java");

        if (Files.isExecutable(java)) {
            return java.toString();
        }

        return "java";
    }

    private static String launcherSource(
            String runId) {

        /*
         * H2 in-memory database.
         *
         * DB_CLOSE_DELAY=-1 keeps the database alive for the
         * lifetime of the JVM even though each repository/service
         * opens its own JDBC connection.
         *
         * The run id isolates the verification database.
         */
        String databaseName =
                "runtime_"
                        + sanitizeDatabaseName(runId);

        String jdbcUrl =
                "jdbc:h2:mem:"
                        + databaseName
                        + ";DB_CLOSE_DELAY=-1";

        return """
                package com.example.generated.urlshortener;

                public final class RuntimeLauncher {

                    private RuntimeLauncher() {
                    }

                    public static void main(String[] args)
                            throws Exception {

                        int port =
                                args.length == 0
                                        ? 0
                                        : Integer.parseInt(args[0]);

                        String jdbcUrl =
                                "%s";

                        JdbcShortUrlRepository repository =
                                new JdbcShortUrlRepository(jdbcUrl);

                        AnalyticsService analytics =
                                new AnalyticsService(jdbcUrl);

                        ShortenerService service =
                                new ShortenerService(
                                        repository,
                                        analytics::record);

                        ShortenerHttpServer server =
                                new ShortenerHttpServer(
                                        service,
                                        analytics);

                        Runtime.getRuntime()
                                .addShutdownHook(
                                        new Thread(
                                                server::stop,
                                                "runtime-shutdown"));

                        server.start(port);

                        System.out.println(
                                "RUNTIME_READY:"
                                        + server.port());

                        System.out.flush();

                        Thread.currentThread().join();
                    }
                }
                """
                .formatted(
                        escapeJava(jdbcUrl));
    }

    private static String sanitizeDatabaseName(
            String runId) {

        if (runId == null
                || runId.isBlank()) {

            return "verification";
        }

        return runId.replaceAll(
                "[^A-Za-z0-9_]",
                "_");
    }

    private static String escapeJava(
            String value) {

        return value
                .replace(
                        "\\",
                        "\\\\")
                .replace(
                        "\"",
                        "\\\"");
    }

    private static final class ProcessOutputReader
            implements Runnable {

        private final InputStream input;
        private final int maxOutput;

        private final StringBuilder output =
                new StringBuilder();

        private volatile int readyPort = -1;

        private ProcessOutputReader(
                InputStream input,
                int maxOutput) {

            this.input = input;
            this.maxOutput = maxOutput;
        }

        @Override
        public void run() {

            try (Reader reader =
                         new InputStreamReader(
                                 input,
                                 StandardCharsets.UTF_8)) {

                char[] buffer =
                        new char[2048];

                int read;

                while ((read = reader.read(buffer)) >= 0) {

                    String chunk =
                            new String(
                                    buffer,
                                    0,
                                    read);

                    synchronized (output) {

                        if (output.length() < maxOutput) {

                            int remaining =
                                    maxOutput
                                            - output.length();

                            output.append(
                                    chunk,
                                    0,
                                    Math.min(
                                            chunk.length(),
                                            remaining));

                            if (output.length() >= maxOutput) {

                                output.append(
                                        "\n...[output truncated]");
                            }

                            /*
                             * Match against accumulated output so that
                             * RUNTIME_READY can be detected even when the
                             * marker is split across stream reads.
                             */
                            Matcher matcher =
                                    READY_PATTERN.matcher(
                                            output);

                            if (matcher.find()) {

                                try {

                                    readyPort =
                                            Integer.parseInt(
                                                    matcher.group(1));

                                } catch (
                                        NumberFormatException ignored) {

                                    /*
                                     * Ignore malformed readiness output.
                                     */
                                }
                            }
                        }
                    }
                }

            } catch (IOException ignored) {

                /*
                 * Child-process output is diagnostic only.
                 */
            }
        }

        int readyPort() {
            return readyPort;
        }

        String output() {

            synchronized (output) {
                return output.toString();
            }
        }
    }
}