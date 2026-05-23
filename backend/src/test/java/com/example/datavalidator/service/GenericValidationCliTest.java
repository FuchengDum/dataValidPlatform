package com.example.datavalidator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenericValidationCliTest {
    @TempDir
    Path tempDir;

    @Test
    void versionPrintsArtifactVersionWithoutStartingValidation() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        int exitCode;
        try {
            System.setOut(new PrintStream(stdout));
            exitCode = cli(objectMapper).run(new String[] {"--version"});
        } finally {
            System.setOut(originalOut);
        }

        assertThat(exitCode).isZero();
        assertThat(stdout.toString().trim()).isEqualTo("data-validator 0.1.0");
    }

    @Test
    void recommendWritesLocalRecommendationJson() throws Exception {
        write("rules.yml", ""
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    ruleName: 应收金额关系校验\n"
                + "    description: amount == total - discount, and amount <= total.\n"
                + "    applicableTables: [bill]\n");
        write("source.yml", ""
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    headers: [id, total, discount, amount]\n");
        Path output = tempDir.resolve("recommendations.json");

        ObjectMapper objectMapper = new ObjectMapper();
        GenericValidationCli cli = new GenericValidationCli(
                new GenericRuleAssetLoader(objectMapper),
                mock(GenericValidationRunner.class),
                new AiAssistService((systemPrompt, userPrompt) -> Optional.empty(), objectMapper),
                new GenericValidationLinter(new GenericRuleAssetLoader(objectMapper)),
                objectMapper);

        int exitCode = cli.run(new String[] {
                "recommend",
                "--rules", tempDir.resolve("rules.yml").toString(),
                "--metadata", tempDir.resolve("source.yml").toString(),
                "--output", output.toString()
        });

        JsonNode recommendation = objectMapper.readTree(output.toFile()).get(0);
        assertThat(exitCode).isZero();
        assertThat(recommendation.get("ruleId").asText()).isEqualTo("C900");
        assertThat(recommendation.get("templateCode").asText()).isEqualTo("ROW_EXPRESSION");
        assertThat(recommendation.get("source").asText()).isEqualTo("LOCAL_RULE_BASED");
        assertThat(recommendation.get("generatedByAi").asBoolean()).isFalse();
        assertThat(recommendation.get("confidence").asText()).isEqualTo("HIGH");
        assertThat(recommendation.get("warnings")).isEmpty();
    }

    @Test
    void recommendWritesCandidateRulePackageAndDiffForHighConfidenceRules() throws Exception {
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    ruleName: 应收金额关系校验\n"
                + "    description: amount == total - discount, and amount <= total.\n"
                + "    applicableTables: [bill]\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    headers: [id, total, discount, amount]\n");
        Path output = tempDir.resolve("recommendations.json");
        Path candidateRules = tempDir.resolve("rules.recommended.yml");

        ObjectMapper objectMapper = new ObjectMapper();
        int exitCode = cli(objectMapper).run(new String[] {
                "recommend",
                "--rules", tempDir.resolve("rules.yml").toString(),
                "--metadata", tempDir.resolve("source.yml").toString(),
                "--output", output.toString(),
                "--candidate-rules", candidateRules.toString()
        });

        JsonNode recommendation = objectMapper.readTree(output.toFile()).get(0);
        String candidateYaml = Files.readString(candidateRules);
        assertThat(exitCode).isZero();
        assertThat(recommendation.get("candidateGenerated").asBoolean()).isTrue();
        assertThat(recommendation.get("diff").get("originalRule").get("ruleId").asText()).isEqualTo("C900");
        assertThat(recommendation.get("diff").get("recommendedTemplate").asText()).isEqualTo("ROW_EXPRESSION");
        assertThat(recommendation.get("diff").get("recommendedReason").asText()).contains("字段间计算关系");
        assertThat(candidateYaml).contains("schemaVersion: '1'");
        assertThat(candidateYaml).contains("ruleId: C900");
        assertThat(candidateYaml).contains("templateCode: ROW_EXPRESSION");
        assertThat(Files.readString(tempDir.resolve("rules.yml"))).doesNotContain("templateCode");
    }

    @Test
    void recommendSkipsExecutableCandidateForLowConfidenceRules() throws Exception {
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C901\n"
                + "    ruleName: 无法自动结构化的复杂人工规则\n"
                + "    description: 人工复核合同附件和线下审批记录。\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    headers: [id]\n");
        Path output = tempDir.resolve("recommendations.json");
        Path candidateRules = tempDir.resolve("rules.recommended.yml");

        ObjectMapper objectMapper = new ObjectMapper();
        GenericValidationCli cli = new GenericValidationCli(
                new GenericRuleAssetLoader(objectMapper),
                mock(GenericValidationRunner.class),
                new AiAssistService((systemPrompt, userPrompt) -> Optional.of(""
                        + "{\"templateCode\":\"NOT_NULL\","
                        + "\"templateParams\":{\"tableName\":\"bill\",\"fields\":[\"id\"]},"
                        + "\"confidence\":\"LOW\",\"explanation\":\"模型低置信推荐，仅供人工参考\"}"), objectMapper),
                new GenericValidationLinter(new GenericRuleAssetLoader(objectMapper)),
                objectMapper);
        int exitCode = cli.run(new String[] {
                "recommend",
                "--rules", tempDir.resolve("rules.yml").toString(),
                "--metadata", tempDir.resolve("source.yml").toString(),
                "--output", output.toString(),
                "--candidate-rules", candidateRules.toString()
        });

        JsonNode recommendation = objectMapper.readTree(output.toFile()).get(0);
        String candidateYaml = Files.readString(candidateRules);
        assertThat(exitCode).isZero();
        assertThat(recommendation.get("confidence").asText()).isEqualTo("LOW");
        assertThat(recommendation.get("candidateGenerated").asBoolean()).isFalse();
        assertThat(recommendation.get("warningDetails").get(0).get("category").asText())
                .isEqualTo("人工确认必需");
        assertThat(candidateYaml).contains("rules: []");
        assertThat(candidateYaml).doesNotContain("ruleId: C901");
    }

    @Test
    void recommendCanPersistAiDebugPromptAndResponse() throws Exception {
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C902\n"
                + "    ruleName: 金额必填\n"
                + "    description: amount must not null.\n"
                + "    applicableTables: [bill]\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    headers: [id, amount]\n");
        ObjectMapper objectMapper = new ObjectMapper();
        GenericValidationCli cli = new GenericValidationCli(
                new GenericRuleAssetLoader(objectMapper),
                mock(GenericValidationRunner.class),
                new AiAssistService((systemPrompt, userPrompt) -> Optional.of(""
                        + "{\"templateCode\":\"NOT_NULL\","
                        + "\"templateParams\":{\"tableName\":\"bill\",\"fields\":[\"amount\"]},"
                        + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐必填字段\"}"), objectMapper),
                new GenericValidationLinter(new GenericRuleAssetLoader(objectMapper)),
                objectMapper);
        Path debugDir = tempDir.resolve("ai-debug");

        int exitCode = cli.run(new String[] {
                "recommend",
                "--rules", tempDir.resolve("rules.yml").toString(),
                "--metadata", tempDir.resolve("source.yml").toString(),
                "--debug-ai", debugDir.toString()
        });

        assertThat(exitCode).isZero();
        assertThat(Files.readString(debugDir.resolve("C902-prompt.txt")))
                .contains("SYSTEM").contains("USER").contains("C902");
        assertThat(Files.readString(debugDir.resolve("C902-response.json")))
                .contains("NOT_NULL").contains("模型推荐必填字段");
    }

    @Test
    void recommendRejectsCandidateRulesPathMatchingSourceRulesPath() throws Exception {
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C903\n"
                + "    ruleName: 金额必填\n"
                + "    description: amount must not null.\n"
                + "    applicableTables: [bill]\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    headers: [id, amount]\n");
        ObjectMapper objectMapper = new ObjectMapper();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;

        int exitCode;
        try {
            System.setErr(new PrintStream(stderr));
            exitCode = cli(objectMapper).run(new String[] {
                    "recommend",
                    "--rules", tempDir.resolve("rules.yml").toString(),
                    "--metadata", tempDir.resolve("source.yml").toString(),
                    "--candidate-rules", tempDir.resolve(".").resolve("rules.yml").toString()
            });
        } finally {
            System.setErr(originalErr);
        }

        assertThat(exitCode).isEqualTo(3);
        assertThat(stderr.toString()).contains("--candidate-rules 不能与 --rules 指向同一文件");
        assertThat(Files.readString(tempDir.resolve("rules.yml"))).contains("description: amount must not null.");
    }

    @Test
    void recommendKeepsStdoutJsonOnlyWhenWritingCandidateRulesWithoutOutput() throws Exception {
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C904\n"
                + "    ruleName: 金额必填\n"
                + "    description: amount must not null.\n"
                + "    applicableTables: [bill]\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    headers: [id, amount]\n");
        ObjectMapper objectMapper = new ObjectMapper();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        Path candidateRules = tempDir.resolve("rules.recommended.yml");

        int exitCode;
        try {
            System.setOut(new PrintStream(stdout));
            System.setErr(new PrintStream(stderr));
            exitCode = cli(objectMapper).run(new String[] {
                    "recommend",
                    "--rules", tempDir.resolve("rules.yml").toString(),
                    "--metadata", tempDir.resolve("source.yml").toString(),
                    "--candidate-rules", candidateRules.toString()
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        JsonNode recommendations = objectMapper.readTree(stdout.toString());
        assertThat(exitCode).isZero();
        assertThat(recommendations.get(0).get("ruleId").asText()).isEqualTo("C904");
        assertThat(stdout.toString()).doesNotContain("候选规则包");
        assertThat(stderr.toString()).contains("候选规则包");
    }

    @Test
    void recommendWarningCategoriesIncludeAiFallbackAndSpecificCause() throws Exception {
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C905\n"
                + "    ruleName: 金额必填\n"
                + "    description: amount must not null.\n"
                + "    applicableTables: [bill]\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    headers: [id, amount]\n");
        ObjectMapper objectMapper = new ObjectMapper();
        GenericValidationCli cli = new GenericValidationCli(
                new GenericRuleAssetLoader(objectMapper),
                mock(GenericValidationRunner.class),
                new AiAssistService((systemPrompt, userPrompt) -> Optional.of(""
                        + "{\"templateCode\":\"NOT_NULL\","
                        + "\"templateParams\":{\"tableName\":\"bill\",\"fields\":[\"missing_amount\"]},"
                        + "\"confidence\":\"HIGH\",\"explanation\":\"模型推荐不存在字段\"}"), objectMapper),
                new GenericValidationLinter(new GenericRuleAssetLoader(objectMapper)),
                objectMapper);
        Path output = tempDir.resolve("recommendations.json");
        Path candidateRules = tempDir.resolve("rules.recommended.yml");

        int exitCode = cli.run(new String[] {
                "recommend",
                "--rules", tempDir.resolve("rules.yml").toString(),
                "--metadata", tempDir.resolve("source.yml").toString(),
                "--output", output.toString(),
                "--candidate-rules", candidateRules.toString()
        });

        JsonNode recommendation = objectMapper.readTree(output.toFile()).get(0);
        assertThat(exitCode).isZero();
        assertThat(recommendation.get("candidateGenerated").asBoolean()).isFalse();
        JsonNode categories = recommendation.get("warningCategories");
        assertThat(categories.toString()).contains("AI降级", "字段缺失");
        assertThat(Files.readString(candidateRules)).contains("rules: []");
    }

    @Test
    void lintRulesWritesStructuredErrorsAndWarnings() throws Exception {
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    ruleName: amount check\n"
                + "    templateCode: ROW_EXPRESSION\n"
                + "    templateParams:\n"
                + "      tableName: bill\n"
                + "      conditions:\n"
                + "        - left: { field: missing_amount }\n"
                + "          operator: ==\n"
                + "          right: { field: amount }\n"
                + "  - ruleId: C900\n"
                + "    ruleName: old template\n"
                + "    templateCode: FIELD_EQUALS\n"
                + "    templateParams:\n"
                + "      source: bill\n"
                + "      target: bill\n"
                + "      key: id\n"
                + "      sourceField: amount\n"
                + "      targetField: amount\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    primaryKey: id\n"
                + "    headers: [id, amount]\n");
        Path output = tempDir.resolve("lint.json");
        ObjectMapper objectMapper = new ObjectMapper();
        GenericValidationCli cli = cli(objectMapper);

        int exitCode = cli.run(new String[] {
                "lint",
                "--rules", tempDir.resolve("rules.yml").toString(),
                "--metadata", tempDir.resolve("source.yml").toString(),
                "--output", output.toString()
        });

        JsonNode result = objectMapper.readTree(output.toFile());
        assertThat(exitCode).isEqualTo(3);
        assertThat(result.get("valid").asBoolean()).isFalse();
        assertThat(result.get("errors")).hasSize(2);
        assertThat(result.get("errors").get(0).get("code").asText()).isEqualTo("UNKNOWN_FIELD");
        assertThat(result.get("errors").get(0).get("path").asText())
                .isEqualTo("rules[0].templateParams.conditions[0].left.field");
        assertThat(result.get("errors").get(0).get("suggestion").asText()).contains("source.yml");
        assertThat(result.get("warnings").get(0).get("code").asText()).isEqualTo("COMPATIBLE_TEMPLATE");
    }

    @Test
    void lintConfigResolvesReferencedAssetsWithoutRunningValidation() throws Exception {
        write("validator.yml", ""
                + "schemaVersion: 1\n"
                + "source:\n"
                + "  type: file\n"
                + "  file: source.yml\n"
                + "rules:\n"
                + "  file: rules.yml\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    primaryKey: id\n"
                + "    headers: [id, amount]\n");
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    ruleName: required amount\n"
                + "    templateCode: NOT_NULL\n"
                + "    templateParams:\n"
                + "      tableName: bill\n"
                + "      fields: [amount]\n");
        Path output = tempDir.resolve("lint-config.json");
        ObjectMapper objectMapper = new ObjectMapper();

        int exitCode = cli(objectMapper).run(new String[] {
                "lint",
                "--config", tempDir.resolve("validator.yml").toString(),
                "--output", output.toString()
        });

        JsonNode result = objectMapper.readTree(output.toFile());
        assertThat(exitCode).isZero();
        assertThat(result.get("valid").asBoolean()).isTrue();
        assertThat(result.get("errors")).isEmpty();
    }

    @Test
    void runConfigCanPrintJsonSummaryAndDisableReportFilesForCi() throws Exception {
        write("validator.yml", ""
                + "schemaVersion: 1\n"
                + "source:\n"
                + "  type: file\n"
                + "  file: source.yml\n"
                + "rules:\n"
                + "  file: rules.yml\n");
        ObjectMapper objectMapper = new ObjectMapper();
        GenericRuleAssetLoader loader = new GenericRuleAssetLoader(objectMapper);
        GenericValidationRunner runner = mock(GenericValidationRunner.class);
        GenericValidationResult result = new GenericValidationResult();
        result.setTotalRules(1);
        result.setExecutedRules(1);
        result.setFindings(new ArrayList<>());
        result.setReportVersion("1");
        result.setToolVersion("test");
        when(runner.run(any(GenericValidationConfig.class), any(Path.class))).thenReturn(result);
        when(runner.exitCode(any(GenericValidationResult.class), any(String.class))).thenReturn(0);
        GenericValidationCli cli = new GenericValidationCli(
                loader,
                runner,
                new AiAssistService((systemPrompt, userPrompt) -> Optional.empty(), objectMapper),
                new GenericValidationLinter(loader),
                objectMapper);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(stdout));
            int exitCode = cli.run(new String[] {
                    "run",
                    "--config", tempDir.resolve("validator.yml").toString(),
                    "--json",
                    "--no-report"
            });

            assertThat(exitCode).isZero();
        } finally {
            System.setOut(originalOut);
        }
        JsonNode summary = objectMapper.readTree(stdout.toString());
        assertThat(summary.get("reportVersion").asText()).isEqualTo("1");
        assertThat(summary.get("exitCode").asInt()).isZero();
        assertThat(summary.get("findingCount").asInt()).isZero();
        assertThat(stdout.toString()).doesNotContain("校验完成");
        ArgumentCaptor<GenericValidationConfig> captor = ArgumentCaptor.forClass(GenericValidationConfig.class);
        org.mockito.Mockito.verify(runner).run(captor.capture(), any(Path.class));
        assertThat(captor.getValue().getValidation().isNoReport()).isTrue();
        assertThat(captor.getValue().getValidation().getCommandSummary()).contains("run --config");
    }

    @Test
    void runConfigQuietSuppressesHumanSummary() throws Exception {
        write("validator.yml", ""
                + "source:\n"
                + "  type: file\n"
                + "rules:\n"
                + "  file: rules.yml\n");
        ObjectMapper objectMapper = new ObjectMapper();
        GenericRuleAssetLoader loader = new GenericRuleAssetLoader(objectMapper);
        GenericValidationRunner runner = mock(GenericValidationRunner.class);
        GenericValidationResult result = new GenericValidationResult();
        result.setFindings(new ArrayList<>());
        when(runner.run(any(GenericValidationConfig.class), any(Path.class))).thenReturn(result);
        when(runner.exitCode(any(GenericValidationResult.class), any(String.class))).thenReturn(0);
        GenericValidationCli cli = new GenericValidationCli(
                loader,
                runner,
                new AiAssistService((systemPrompt, userPrompt) -> Optional.empty(), objectMapper),
                new GenericValidationLinter(loader),
                objectMapper);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(stdout));
            int exitCode = cli.run(new String[] {
                    "run",
                    "--config", tempDir.resolve("validator.yml").toString(),
                    "--quiet"
            });

            assertThat(exitCode).isZero();
        } finally {
            System.setOut(originalOut);
        }
        assertThat(stdout.toString()).isEmpty();
    }

    @Test
    void runConfigJsonPrintsStructuredErrorForConfigFailures() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GenericValidationCli cli = cli(objectMapper);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setOut(new PrintStream(stdout));
            System.setErr(new PrintStream(stderr));
            int exitCode = cli.run(new String[] {
                    "run",
                    "--config", tempDir.resolve("missing-validator.yml").toString(),
                    "--json",
                    "--no-report"
            });

            assertThat(exitCode).isEqualTo(3);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        JsonNode summary = objectMapper.readTree(stdout.toString());
        assertThat(summary.get("reportVersion").asText()).isEqualTo("1");
        assertThat(summary.get("exitCode").asInt()).isEqualTo(3);
        assertThat(summary.get("error").get("code").asText()).isEqualTo("CONFIG_ERROR");
        assertThat(summary.get("error").get("message").asText()).contains("配置文件不存在");
        assertThat(stderr.toString()).isEmpty();
    }

    @Test
    void lintConfigRejectsEmptyConfigFile() throws Exception {
        write("validator.yml", "");
        Path output = tempDir.resolve("lint-empty.json");
        ObjectMapper objectMapper = new ObjectMapper();

        int exitCode = cli(objectMapper).run(new String[] {
                "lint",
                "--config", tempDir.resolve("validator.yml").toString(),
                "--output", output.toString()
        });

        JsonNode result = objectMapper.readTree(output.toFile());
        assertThat(exitCode).isEqualTo(3);
        assertThat(result.get("valid").asBoolean()).isFalse();
        assertThat(result.get("errors").get(0).get("code").asText()).isEqualTo("EMPTY_CONFIG");
    }

    @Test
    void lintRulesRejectsUnsupportedSchemaVersion() throws Exception {
        write("rules.yml", ""
                + "schemaVersion: 999\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    templateCode: NOT_NULL\n"
                + "    templateParams:\n"
                + "      tableName: bill\n"
                + "      fields: [amount]\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    primaryKey: id\n"
                + "    headers: [id, amount]\n");
        Path output = tempDir.resolve("lint-schema.json");
        ObjectMapper objectMapper = new ObjectMapper();

        int exitCode = cli(objectMapper).run(new String[] {
                "lint",
                "--rules", tempDir.resolve("rules.yml").toString(),
                "--metadata", tempDir.resolve("source.yml").toString(),
                "--output", output.toString()
        });

        JsonNode result = objectMapper.readTree(output.toFile());
        assertThat(exitCode).isEqualTo(3);
        assertThat(result.get("errors").get(0).get("code").asText()).isEqualTo("UNSUPPORTED_SCHEMA_VERSION");
        assertThat(result.get("errors").get(0).get("path").asText()).isEqualTo("schemaVersion");
    }

    @Test
    void lintRulesHandlesMissingTemplateParamsAsConfigError() throws Exception {
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    templateCode: DUPLICATE_ASSERT\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    primaryKey: id\n"
                + "    headers: [id, amount]\n");
        Path output = tempDir.resolve("lint-missing-params.json");
        ObjectMapper objectMapper = new ObjectMapper();

        int exitCode = cli(objectMapper).run(new String[] {
                "lint",
                "--rules", tempDir.resolve("rules.yml").toString(),
                "--metadata", tempDir.resolve("source.yml").toString(),
                "--output", output.toString()
        });

        JsonNode result = objectMapper.readTree(output.toFile());
        assertThat(exitCode).isEqualTo(3);
        assertThat(result.get("errors").get(0).get("code").asText()).isEqualTo("MISSING_TEMPLATE_PARAMS");
        assertThat(result.get("errors").get(0).get("path").asText()).isEqualTo("rules[0].templateParams");
    }

    @Test
    void lintRulesRejectsUnsupportedCategoryAndSeverity() throws Exception {
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    category: CROSS_TABLE_RULE\n"
                + "    severity: BLOCKER\n"
                + "    templateCode: NOT_NULL\n"
                + "    templateParams:\n"
                + "      tableName: bill\n"
                + "      fields: [amount]\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    primaryKey: id\n"
                + "    headers: [id, amount]\n");
        Path output = tempDir.resolve("lint-enum.json");
        ObjectMapper objectMapper = new ObjectMapper();

        int exitCode = cli(objectMapper).run(new String[] {
                "lint",
                "--rules", tempDir.resolve("rules.yml").toString(),
                "--metadata", tempDir.resolve("source.yml").toString(),
                "--output", output.toString()
        });

        JsonNode result = objectMapper.readTree(output.toFile());
        assertThat(exitCode).isEqualTo(3);
        assertThat(result.get("errors").findValuesAsText("code"))
                .contains("UNSUPPORTED_CATEGORY", "UNSUPPORTED_SEVERITY");
    }

    @Test
    void lintRulesReportsRelationExistsFieldPath() throws Exception {
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    templateCode: RELATION_EXISTS\n"
                + "    templateParams:\n"
                + "      source: bill\n"
                + "      target: payment\n"
                + "      keys:\n"
                + "        - sourceField: bill_id\n"
                + "          targetField: missing_bill_id\n"
                + "      expectExists: true\n");
        write("source.yml", ""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: bill\n"
                + "    primaryKey: bill_id\n"
                + "    headers: [bill_id, amount]\n"
                + "  - logicalName: payment\n"
                + "    primaryKey: payment_id\n"
                + "    headers: [payment_id, bill_id]\n");
        Path output = tempDir.resolve("lint-relation.json");
        ObjectMapper objectMapper = new ObjectMapper();

        int exitCode = cli(objectMapper).run(new String[] {
                "lint",
                "--rules", tempDir.resolve("rules.yml").toString(),
                "--metadata", tempDir.resolve("source.yml").toString(),
                "--output", output.toString()
        });

        JsonNode result = objectMapper.readTree(output.toFile());
        assertThat(exitCode).isEqualTo(3);
        assertThat(result.get("errors").get(0).get("code").asText()).isEqualTo("UNKNOWN_FIELD");
        assertThat(result.get("errors").get(0).get("path").asText())
                .isEqualTo("rules[0].templateParams.keys[0].targetField");
    }

    @Test
    void lintConfigRejectsUnsafeJdbcConfigWithoutConnecting() throws Exception {
        write("validator.yml", ""
                + "schemaVersion: 1\n"
                + "source:\n"
                + "  type: jdbc\n"
                + "  jdbc:\n"
                + "    url: jdbc:mysql://127.0.0.1:3306/biz?password=plain-text\n"
                + "    driverClassName: com.mysql.cj.jdbc.Driver\n"
                + "    username: readonly\n"
                + "    password: plain-text\n"
                + "    dialect: mysql\n"
                + "    maxRows: 0\n"
                + "  tables:\n"
                + "    - logicalName: bill\n"
                + "      physicalName: bill\n"
                + "      primaryKey: id\n"
                + "      headers: [id, amount]\n"
                + "      sql: SELECT * FROM bill\n"
                + "rules:\n"
                + "  file: rules.yml\n");
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    templateCode: NOT_NULL\n"
                + "    templateParams:\n"
                + "      tableName: bill\n"
                + "      fields: [amount]\n");
        Path output = tempDir.resolve("lint-jdbc.json");
        ObjectMapper objectMapper = new ObjectMapper();

        int exitCode = cli(objectMapper).run(new String[] {
                "lint",
                "--config", tempDir.resolve("validator.yml").toString(),
                "--output", output.toString()
        });

        JsonNode result = objectMapper.readTree(output.toFile());
        assertThat(exitCode).isEqualTo(3);
        assertThat(result.get("errors").findValuesAsText("code"))
                .contains("PLAINTEXT_JDBC_PASSWORD", "MISSING_JDBC_PASSWORD_ENV",
                        "JDBC_URL_CONTAINS_SECRET", "INVALID_JDBC_MAX_ROWS", "UNSAFE_SQL");
    }

    @Test
    void lintConfigAcceptsMysqlPasswordEnvNameWithoutCheckingCurrentEnvironment() throws Exception {
        write("validator.yml", ""
                + "schemaVersion: 1\n"
                + "source:\n"
                + "  type: jdbc\n"
                + "  jdbc:\n"
                + "    url: jdbc:mysql://127.0.0.1:3306/biz\n"
                + "    driverClassName: com.mysql.cj.jdbc.Driver\n"
                + "    username: readonly\n"
                + "    passwordEnv: DEFINITELY_ABSENT_STAGE7_PASSWORD\n"
                + "    dialect: mysql\n"
                + "    maxRows: 100\n"
                + "  tables:\n"
                + "    - logicalName: bill\n"
                + "      physicalName: bill\n"
                + "      primaryKey: id\n"
                + "      headers: [id, amount]\n"
                + "rules:\n"
                + "  file: rules.yml\n");
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    templateCode: NOT_NULL\n"
                + "    templateParams:\n"
                + "      tableName: bill\n"
                + "      fields: [amount]\n");
        Path output = tempDir.resolve("lint-mysql-template.json");
        ObjectMapper objectMapper = new ObjectMapper();

        int exitCode = cli(objectMapper).run(new String[] {
                "lint",
                "--config", tempDir.resolve("validator.yml").toString(),
                "--output", output.toString()
        });

        JsonNode result = objectMapper.readTree(output.toFile());
        assertThat(exitCode).isZero();
        assertThat(result.get("errors")).isEmpty();
    }

    @Test
    void lintConfigAcceptsJdbcTableModeWithH2NoPassword() throws Exception {
        write("validator.yml", ""
                + "schemaVersion: 1\n"
                + "source:\n"
                + "  type: jdbc\n"
                + "  jdbc:\n"
                + "    url: jdbc:h2:mem:lint_ok\n"
                + "    driverClassName: org.h2.Driver\n"
                + "    username: sa\n"
                + "    dialect: h2\n"
                + "    maxRows: 100\n"
                + "  tables:\n"
                + "    - logicalName: bill\n"
                + "      physicalName: billing.bill\n"
                + "      primaryKey: id\n"
                + "      headers: [id, amount]\n"
                + "      fieldMappings:\n"
                + "        amount: bill_amount\n"
                + "rules:\n"
                + "  file: rules.yml\n");
        write("rules.yml", ""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    templateCode: NOT_NULL\n"
                + "    templateParams:\n"
                + "      tableName: bill\n"
                + "      fields: [amount]\n");
        Path output = tempDir.resolve("lint-jdbc-ok.json");
        ObjectMapper objectMapper = new ObjectMapper();

        int exitCode = cli(objectMapper).run(new String[] {
                "lint",
                "--config", tempDir.resolve("validator.yml").toString(),
                "--output", output.toString()
        });

        JsonNode result = objectMapper.readTree(output.toFile());
        assertThat(exitCode).isZero();
        assertThat(result.get("errors")).isEmpty();
    }

    private GenericValidationCli cli(ObjectMapper objectMapper) {
        GenericRuleAssetLoader loader = new GenericRuleAssetLoader(objectMapper);
        return new GenericValidationCli(
                loader,
                mock(GenericValidationRunner.class),
                new AiAssistService((systemPrompt, userPrompt) -> Optional.empty(), objectMapper),
                new GenericValidationLinter(loader),
                objectMapper);
    }

    private void write(String fileName, String content) throws Exception {
        Files.writeString(tempDir.resolve(fileName), content);
    }
}
