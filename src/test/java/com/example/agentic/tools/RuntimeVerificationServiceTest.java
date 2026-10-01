
        package com.example.agentic.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agentic.config.WorkspaceProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuntimeVerificationServiceTest {

    private Path projectRoot;
    private Path workspaceRoot;
    private Path generatedWorkspace;

    @BeforeEach
    void setUp() throws IOException {

        projectRoot =
                Path.of(System.getProperty("user.dir"))
                        .toAbsolutePath()
                        .normalize();

        workspaceRoot =
                Files.createTempDirectory(
                        projectRoot,
                        "runtime-verification-test-");

        generatedWorkspace =
                workspaceRoot.resolve("runtime-test");

        Files.createDirectories(generatedWorkspace);

        /*
         * Use the current reference POM so this test exercises
         * Batch 7 executable-JAR packaging with the Maven Shade plugin.
         */
        Files.copy(
                projectRoot.resolve("reference/sample/pom.xml"),
                generatedWorkspace.resolve("pom.xml"),
                StandardCopyOption.REPLACE_EXISTING);

        /*
         * Reuse the known-good generated URL-shortener source tree
         * from the previous completed run.
         *
         * This test is intentionally not responsible for testing
         * LLM generation. Its responsibility is:
         *
         *     materialized project
         *          -> executable JAR
         *          -> process startup
         *          -> HTTP verification
         */
        Path knownGoodWorkspace =
                projectRoot.resolve(
                        "workspaces/cfb25686-95ab-4d85-894f-39f521c0e72f");

        copyGeneratedSourceTree(
                knownGoodWorkspace.resolve("src"),
                generatedWorkspace.resolve("src"));
    }

    @AfterEach
    void tearDown() throws IOException {

        if (workspaceRoot == null || !Files.exists(workspaceRoot)) {
            return;
        }

        try (var stream = Files.walk(workspaceRoot)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException(
                                    "Unable to clean test workspace: "
                                            + path,
                                    e);
                        }
                    });
        }
    }

    @Test
    void verifiesGeneratedUrlShortenerAtRuntime() {

        assertTrue(
                Files.isDirectory(generatedWorkspace),
                "Generated workspace does not exist: "
                        + generatedWorkspace);

        assertTrue(
                Files.isRegularFile(
                        generatedWorkspace.resolve("pom.xml")),
                "Generated workspace does not contain pom.xml");

        assertTrue(
                Files.isRegularFile(
                        generatedWorkspace.resolve(
                                "src/main/java/com/example/generated/urlshortener/"
                                        + "ShortenerHttpServer.java")),
                "Generated workspace does not contain "
                        + "ShortenerHttpServer.java");

        assertTrue(
                Files.isRegularFile(
                        generatedWorkspace.resolve(
                                "src/main/java/com/example/generated/urlshortener/"
                                        + "JdbcShortUrlRepository.java")),
                "Generated workspace does not contain "
                        + "JdbcShortUrlRepository.java");

        WorkspaceProperties properties =
                new WorkspaceProperties(
                        workspaceRoot,
                        projectRoot.resolve("reference/sample"),
                        projectRoot.resolve("reference/sample"),
                        Duration.ofMinutes(3),
                        50_000,
                        2_000);

        WorkspaceManager workspaceManager =
                new WorkspaceManager(properties);

        CompileAndTestRunner compileAndTestRunner =
                new CompileAndTestRunner(properties);

        ExecutableArtifactService executableArtifactService =
                new ExecutableArtifactService(
                        workspaceManager,
                        compileAndTestRunner);

        RuntimeVerificationService service =
                new RuntimeVerificationService(
                        workspaceManager,
                        properties,
                        executableArtifactService);

        RuntimeVerificationResult result =
                service.verify("runtime-test");

        assertNotNull(result);

        assertTrue(
                result.passed(),
                () -> "Runtime verification failed: "
                        + result.failureReason());

        assertTrue(
                result.port() > 0,
                "Runtime verifier should report an allocated port");

        assertNotNull(result.duration());

        assertFalse(
                result.duration().isNegative(),
                "Runtime verification duration must not be negative");

        assertNotNull(result.checks());

        /*
         * Batch 7 executable artifact evidence.
         */
        RuntimeVerificationResult.CheckResult packageCheck =
                findCheck(result, "package");

        assertTrue(
                packageCheck.passed(),
                "Executable JAR packaging should pass");

        /*
         * Verify that the executable artifact was actually retained
         * in the generated workspace after runtime verification.
         */
        Path targetDirectory =
                generatedWorkspace.resolve("target");

        assertTrue(
                Files.isDirectory(targetDirectory),
                "Maven target directory should exist");

        assertTrue(
                containsExecutableJar(targetDirectory),
                "Generated workspace should contain an executable JAR");

        /*
         * Runtime startup verification.
         */
        RuntimeVerificationResult.CheckResult startup =
                findCheck(result, "startup");

        assertTrue(
                startup.passed(),
                "Generated executable JAR should start");

        /*
         * POST /shorten
         */
        RuntimeVerificationResult.CheckResult create =
                findCheck(result, "create-link");

        assertTrue(
                create.passed(),
                "Create-link verification should pass");

        assertEquals(
                201,
                create.expectedStatus());

        assertEquals(
                201,
                create.actualStatus());

        /*
         * GET /{code}
         */
        RuntimeVerificationResult.CheckResult redirect =
                findCheck(result, "redirect");

        assertTrue(
                redirect.passed(),
                "Redirect verification should pass");

        assertEquals(
                302,
                redirect.expectedStatus());

        assertEquals(
                302,
                redirect.actualStatus());

        /*
         * GET /analytics/{code}
         */
        RuntimeVerificationResult.CheckResult analytics =
                findCheck(result, "analytics");

        assertTrue(
                analytics.passed(),
                "Analytics verification should pass");

        assertEquals(
                200,
                analytics.expectedStatus());

        assertEquals(
                200,
                analytics.actualStatus());

        /*
         * Unknown short-code behavior.
         */
        RuntimeVerificationResult.CheckResult notFound =
                findCheck(result, "not-found");

        assertTrue(
                notFound.passed(),
                "Unknown-link verification should pass");

        assertEquals(
                404,
                notFound.expectedStatus());

        assertEquals(
                404,
                notFound.actualStatus());
    }

    private static void copyGeneratedSourceTree(
            Path source,
            Path destination) throws IOException {

        if (!Files.isDirectory(source)) {
            throw new IllegalStateException(
                    "Known-good generated source tree does not exist: "
                            + source);
        }

        try (var stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path relative =
                            source.relativize(path);

                    Path target =
                            destination.resolve(relative);

                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(
                                target.getParent());

                        Files.copy(
                                path,
                                target,
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(
                            "Unable to copy generated source: "
                                    + path,
                            e);
                }
            });
        }
    }

    private static boolean containsExecutableJar(
            Path targetDirectory) {

        try (var files =
                     Files.list(targetDirectory)) {

            return files
                    .filter(Files::isRegularFile)
                    .map(path ->
                            path.getFileName()
                                    .toString())
                    .anyMatch(name ->
                            name.endsWith(".jar")
                                    && !name.endsWith("-original.jar"));

        } catch (Exception e) {

            throw new AssertionError(
                    "Unable to inspect target directory: "
                            + targetDirectory,
                    e);
        }
    }

    private static RuntimeVerificationResult.CheckResult findCheck(
            RuntimeVerificationResult result,
            String name) {

        return result.checks()
                .stream()
                .filter(check ->
                        name.equals(check.name()))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "Missing runtime check: "
                                        + name));
    }
}

