package com.example.datavalidator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GenericValidationCliInitTest {
    @TempDir
    Path tempDir;

    @Test
    void helpUsageIncludesInitJdbcCommand() throws Exception {
        InvocationResult result = invoke("--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("data-validator init jdbc [--template order-fulfillment] --output <dir>");
    }

    @Test
    void initJdbcGeneratesDefaultReadonlySkeleton() throws Exception {
        Path outputDir = tempDir.resolve("generic-jdbc");

        int exitCode = cli().run(new String[] {
                "init",
                "jdbc",
                "--output", outputDir.toString()
        });

        assertThat(exitCode).isZero();
        assertThat(outputDir.resolve("validator.yml")).exists();
        assertThat(outputDir.resolve("source.yml")).exists();
        assertThat(outputDir.resolve("rules.yml")).exists();
        assertThat(outputDir.resolve("README.md")).exists();
        assertThat(Files.readString(outputDir.resolve("validator.yml")))
                .contains("source.yml")
                .contains("rules.yml");
        assertThat(Files.readString(outputDir.resolve("source.yml")))
                .contains("type: jdbc")
                .contains("driverClassName: org.h2.Driver")
                .doesNotContain("password:")
                .doesNotContain(tempDir.toString());
        assertThat(Files.readString(outputDir.resolve("README.md")))
                .contains("passwordEnv")
                .contains("fieldMappings")
                .doesNotContain(tempDir.toString());

        Path lintOutput = tempDir.resolve("default-lint.json");
        int lintExitCode = cli().run(new String[] {
                "lint",
                "--config", outputDir.resolve("validator.yml").toString(),
                "--output", lintOutput.toString()
        });

        JsonNode lintResult = new ObjectMapper().readTree(lintOutput.toFile());
        assertThat(lintExitCode).isZero();
        assertThat(lintResult.get("valid").asBoolean()).isTrue();

        InvocationResult runResult = invoke(
                "run",
                "--config", outputDir.resolve("validator.yml").toString(),
                "--json",
                "--no-report");
        JsonNode runSummary = new ObjectMapper().readTree(runResult.stdout());
        assertThat(runResult.exitCode()).isEqualTo(2);
        assertThat(runSummary.get("executedRules").asInt()).isEqualTo(2);
        assertThat(runSummary.get("sourceSummary").get("tableCount").asInt()).isEqualTo(2);
    }

    @Test
    void initJdbcOrderFulfillmentGeneratesRunnableTemplate() throws Exception {
        Path outputDir = tempDir.resolve("order-fulfillment");
        ObjectMapper objectMapper = new ObjectMapper();

        int initExitCode = cli().run(new String[] {
                "init",
                "jdbc",
                "--template", "order-fulfillment",
                "--output", outputDir.toString()
        });

        assertThat(initExitCode).isZero();
        assertThat(Files.readString(outputDir.resolve("validator.yml")))
                .contains("source.yml")
                .contains("rules.yml");
        assertThat(Files.readString(outputDir.resolve("source.yml")))
                .contains("jdbc:h2:mem:case5_seed_init;MODE=MySQL;DB_CLOSE_DELAY=-1;INIT=DROP ALL OBJECTS\\\\;RUNSCRIPT FROM 'classpath:db/migration/V3__seed_case5_business_tables.sql'")
                .doesNotContain("password:")
                .doesNotContain(tempDir.toString());
        assertThat(Files.readString(outputDir.resolve("rules.yml"))).isEqualTo(resourceText("case5/rules.yml"));
        assertThat(Files.readString(outputDir.resolve("README.md")))
                .contains("passwordEnv")
                .contains("physicalName")
                .contains("fieldMappings")
                .doesNotContain(tempDir.toString());

        Path lintOutput = tempDir.resolve("order-lint.json");
        int lintExitCode = cli().run(new String[] {
                "lint",
                "--config", outputDir.resolve("validator.yml").toString(),
                "--output", lintOutput.toString()
        });

        JsonNode lintResult = objectMapper.readTree(lintOutput.toFile());
        assertThat(lintExitCode).isZero();
        assertThat(lintResult.get("valid").asBoolean()).isTrue();

        InvocationResult runResult = invoke(
                "run",
                "--config", outputDir.resolve("validator.yml").toString(),
                "--json",
                "--no-report");
        JsonNode runSummary = objectMapper.readTree(runResult.stdout());
        assertThat(runResult.exitCode()).isEqualTo(2);
        assertThat(runSummary.get("executedRules").asInt()).isEqualTo(30);
        assertThat(runSummary.get("sourceSummary").get("tableCount").asInt()).isEqualTo(5);
    }

    @Test
    void initJdbcRefusesToOverwriteExistingFiles() throws Exception {
        Path outputDir = tempDir.resolve("existing-jdbc");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("rules.yml"), "keep-me");

        InvocationResult result = invoke(
                "init",
                "jdbc",
                "--output", outputDir.toString());

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.stderr()).contains("目标文件已存在").contains("rules.yml");
        assertThat(Files.readString(outputDir.resolve("rules.yml"))).isEqualTo("keep-me");
        assertThat(outputDir.resolve("validator.yml")).doesNotExist();
    }

    private InvocationResult invoke(String... args) throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        int exitCode;
        try {
            System.setOut(new PrintStream(stdout));
            System.setErr(new PrintStream(stderr));
            exitCode = cli().run(args);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return new InvocationResult(exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private GenericValidationCli cli() {
        ObjectMapper objectMapper = new ObjectMapper();
        GenericRuleAssetLoader loader = new GenericRuleAssetLoader(objectMapper);
        GenericDataSourceProvider dataSourceProvider = new GenericDataSourceProvider(loader, mock(JdbcTemplate.class));
        GenericValidationRunner runner = new GenericValidationRunner(
                loader,
                dataSourceProvider,
                new GenericValidationReportWriter(objectMapper),
                new TemplateRuleExecutor());
        return new GenericValidationCli(
                loader,
                runner,
                new AiAssistService((systemPrompt, userPrompt) -> Optional.empty(), objectMapper),
                new GenericValidationLinter(loader),
                objectMapper);
    }

    private String resourceText(String path) throws Exception {
        try (InputStream inputStream = GenericValidationCliInitTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static class InvocationResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        private InvocationResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        private int exitCode() {
            return exitCode;
        }

        private String stdout() {
            return stdout;
        }

        private String stderr() {
            return stderr;
        }
    }
}
