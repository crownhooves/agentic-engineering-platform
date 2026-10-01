package com.example.agentic.tools;

import com.example.agentic.config.WorkspaceProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Runs Maven without a shell, with a timeout and bounded output capture.
 *
 * <p>Compilation is executed before tests. Test execution is skipped when
 * compilation fails.
 *
 * <p>Test execution evidence is obtained primarily from Maven Surefire XML
 * reports and falls back to Maven console output when reports are unavailable.
 *
 * <p>This component also supports controlled Maven packaging for producing
 * an executable JAR. No arbitrary command supplied by a model is permitted.
 */
@Component
public final class CompileAndTestRunner {

    private static final Pattern CONSOLE_TESTS_RUN =
            Pattern.compile(
                    "Tests run:\\s*(\\d+),\\s*"
                            + "Failures:\\s*(\\d+),\\s*"
                            + "Errors:\\s*(\\d+)"
                            + "(?:,\\s*Skipped:\\s*(\\d+))?");

    private final WorkspaceProperties properties;

    public CompileAndTestRunner(WorkspaceProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates a workspace by compiling it first and then executing tests.
     *
     * @param workspace isolated workspace to validate
     * @return immutable build/test result
     */
    public BuildResult validate(Path workspace) {
        validateWorkspace(workspace);

        InstantClock clock = new InstantClock();

        CommandResult compile =
                run(
                        workspace,
                        List.of(
                                mavenExecutable(workspace),
                                "-q",
                                "-DskipTests",
                                "compile"));

        if (!compile.completed()) {
            return new BuildResult(
                    false,
                    false,
                    false,
                    compile.exitCode(),
                    0,
                    0,
                    compile.output(),
                    clock.elapsed());
        }

        if (compile.exitCode() != 0) {
            return new BuildResult(
                    false,
                    false,
                    false,
                    compile.exitCode(),
                    0,
                    0,
                    compile.output(),
                    clock.elapsed());
        }

        /*
         * Compilation succeeded, so tests are now executed separately.
         *
         * Surefire XML reports are used as the primary source of test
         * execution evidence. Console parsing remains as a fallback.
         */
        CommandResult tests =
                run(
                        workspace,
                        List.of(
                                mavenExecutable(workspace),
                                "-q",
                                "test"));

        TestCounts counts =
                readSurefireReports(workspace);

        if (counts.run() == 0) {
            counts = parseTests(tests.output());
        }

        boolean testsExecuted =
                tests.completed()
                        && counts.run() > 0;

        boolean success =
                tests.completed()
                        && tests.exitCode() == 0
                        && testsExecuted
                        && counts.failures() == 0
                        && counts.errors() == 0;

        String combinedOutput =
                compile.output()
                        + "\n"
                        + tests.output();

        return new BuildResult(
                success,
                true,
                testsExecuted,
                tests.exitCode(),
                counts.run(),
                counts.failures() + counts.errors(),
                combinedOutput,
                clock.elapsed());
    }

    /**
     * Packages the workspace using the project's Maven build.
     *
     * <p>The generated project is responsible for configuring the executable
     * JAR, including its Main-Class and any required runtime dependencies.
     *
     * <p>Only the fixed Maven package operation is permitted. The model
     * cannot supply arbitrary commands or command arguments.
     *
     * @param workspace isolated workspace to package
     * @return immutable packaging result
     */
    public PackageResult packageExecutable(Path workspace) {
        validateWorkspace(workspace);

        InstantClock clock = new InstantClock();

        CommandResult packageResult =
                run(
                        workspace,
                        List.of(
                                mavenExecutable(workspace),
                                "-q",
                                "-DskipTests",
                                "package"));

        return new PackageResult(
                packageResult.completed()
                        && packageResult.exitCode() == 0,
                packageResult.exitCode(),
                packageResult.output(),
                clock.elapsed());
    }

    /**
     * Executes a controlled process without invoking a shell.
     *
     * <p>The command list is constructed internally by this class. No shell
     * interpolation is performed, which prevents generated requirements or
     * model output from becoming shell commands.
     */
    private CommandResult run(
            Path workspace,
            List<String> command) {

        Process process = null;

        try {
            process =
                    new ProcessBuilder(command)
                            .directory(workspace.toFile())
                            .redirectErrorStream(true)
                            .start();

            StringBuilder output =
                    new StringBuilder();

            Process finalProcess = process;

            Thread reader =
                    new Thread(
                            () ->
                                    readBounded(
                                            finalProcess.getInputStream(),
                                            output,
                                            properties.maxOutputChars()),
                            "maven-output-reader");

            reader.setDaemon(true);
            reader.start();

            boolean finished =
                    process.waitFor(
                            properties.commandTimeout().toMillis(),
                            TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroy();

                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }

                reader.join(1_000);

                return new CommandResult(
                        false,
                        -1,
                        output
                                + "\n[COMMAND TIMED OUT after "
                                + properties.commandTimeout()
                                + "]");
            }

            reader.join(2_000);

            return new CommandResult(
                    true,
                    process.exitValue(),
                    output.toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            if (process != null) {
                process.destroyForcibly();
            }

            return new CommandResult(
                    false,
                    -1,
                    "Interrupted while running command");

        } catch (IOException e) {
            return new CommandResult(
                    false,
                    -1,
                    "Cannot execute command: "
                            + e.getMessage());
        }
    }

    /**
     * Captures process output up to the configured maximum.
     */
    private static void readBounded(
            InputStream input,
            StringBuilder output,
            int max) {

        try (Reader reader =
                     new InputStreamReader(
                             input,
                             StandardCharsets.UTF_8)) {

            char[] buffer =
                    new char[2048];

            int read;

            while ((read = reader.read(buffer)) >= 0) {

                synchronized (output) {

                    if (output.length() < max) {

                        int remaining =
                                max - output.length();

                        output.append(
                                buffer,
                                0,
                                Math.min(
                                        read,
                                        remaining));

                        if (output.length() >= max) {
                            output.append(
                                    "\n...[output truncated]");
                        }
                    }
                }
            }

        } catch (IOException ignored) {
            /*
             * The process exit code remains authoritative for command
             * execution. Output capture failure must not mask it.
             */
        }
    }

    /**
     * Uses the Maven wrapper when present and executable.
     */
    private static String mavenExecutable(
            Path workspace) {

        Path wrapper =
                workspace.resolve("mvnw");

        if (Files.isExecutable(wrapper)) {
            return "./mvnw";
        }

        Path wrapperBat =
                workspace.resolve("mvnw.cmd");

        if (Files.isRegularFile(wrapperBat)
                && System.getProperty("os.name", "")
                .toLowerCase()
                .contains("win")) {

            return "mvnw.cmd";
        }

        return "mvn";
    }

    /**
     * Reads Maven Surefire XML reports from
     * target/surefire-reports.
     *
     * <p>Typical Surefire report attributes are:
     *
     * <pre>
     * tests="1"
     * failures="0"
     * errors="0"
     * skipped="0"
     * </pre>
     */
    private static TestCounts readSurefireReports(
            Path workspace) {

        Path reportsDirectory =
                workspace
                        .resolve("target")
                        .resolve("surefire-reports");

        if (!Files.isDirectory(reportsDirectory)) {
            return new TestCounts(
                    0,
                    0,
                    0);
        }

        int run = 0;
        int failures = 0;
        int errors = 0;

        try (var files =
                     Files.list(reportsDirectory)) {

            List<Path> reports =
                    files
                            .filter(Files::isRegularFile)
                            .filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .endsWith(".xml"))
                            .toList();

            for (Path report : reports) {

                TestCounts counts =
                        parseSurefireReport(report);

                run += counts.run();
                failures += counts.failures();
                errors += counts.errors();
            }

        } catch (IOException e) {
            return new TestCounts(
                    0,
                    0,
                    0);
        }

        return new TestCounts(
                run,
                failures,
                errors);
    }

    /**
     * Parses a single Surefire XML report.
     */
    private static TestCounts parseSurefireReport(
            Path report) {

        try {

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            /*
             * Harden XML parsing against external entities and DTDs.
             */
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true);

            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false);

            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false);

            factory.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);

            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document =
                    factory
                            .newDocumentBuilder()
                            .parse(report.toFile());

            Element root =
                    document.getDocumentElement();

            int run =
                    attributeInt(
                            root,
                            "tests");

            int failures =
                    attributeInt(
                            root,
                            "failures");

            int errors =
                    attributeInt(
                            root,
                            "errors");

            return new TestCounts(
                    run,
                    failures,
                    errors);

        } catch (Exception ignored) {
            return new TestCounts(
                    0,
                    0,
                    0);
        }
    }

    /**
     * Reads an integer XML attribute safely.
     */
    private static int attributeInt(
            Element element,
            String name) {

        String value =
                element.getAttribute(name);

        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Fallback parser for Maven/Surefire console output.
     */
    private static TestCounts parseTests(
            String output) {

        Matcher matcher =
                CONSOLE_TESTS_RUN.matcher(output);

        int run = 0;
        int failures = 0;
        int errors = 0;

        while (matcher.find()) {

            run +=
                    Integer.parseInt(
                            matcher.group(1));

            failures +=
                    Integer.parseInt(
                            matcher.group(2));

            errors +=
                    Integer.parseInt(
                            matcher.group(3));
        }

        return new TestCounts(
                run,
                failures,
                errors);
    }

    /**
     * Validates the supplied workspace before any process is started.
     */
    private static void validateWorkspace(
            Path workspace) {

        if (workspace == null
                || !Files.isDirectory(workspace)) {

            throw new IllegalArgumentException(
                    "Workspace directory does not exist: "
                            + workspace);
        }

        Path normalized =
                workspace
                        .toAbsolutePath()
                        .normalize();

        /*
         * Maven must operate against a directory, never a regular file.
         */
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException(
                    "Workspace is not a directory: "
                            + workspace);
        }
    }

    private record CommandResult(
            boolean completed,
            int exitCode,
            String output) {
    }

    private record TestCounts(
            int run,
            int failures,
            int errors) {
    }

    /**
     * Result of Maven packaging.
     *
     * @param success whether Maven completed successfully
     * @param exitCode Maven process exit code, or -1 when it did not complete
     * @param output bounded Maven output
     * @param duration total packaging duration
     */
    public record PackageResult(
            boolean success,
            int exitCode,
            String output,
            Duration duration) {
    }

    private static final class InstantClock {

        private final long start =
                System.nanoTime();

        Duration elapsed() {
            return Duration.ofNanos(
                    System.nanoTime() - start);
        }
    }
}