package com.example.datavalidator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GenericValidationCliTest {
    @TempDir
    Path tempDir;

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
