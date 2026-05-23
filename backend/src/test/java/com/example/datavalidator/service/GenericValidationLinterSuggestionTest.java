package com.example.datavalidator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GenericValidationLinterSuggestionTest {
    @TempDir
    Path tempDir;

    private final GenericValidationLinter linter =
            new GenericValidationLinter(new GenericRuleAssetLoader(new ObjectMapper()));

    @Test
    void suggestsPasswordEnvForMysqlJdbcSource() throws Exception {
        writeJdbcConfig(""
                + "schemaVersion: 1\n"
                + "type: jdbc\n"
                + "jdbc:\n"
                + "  url: jdbc:mysql://db.example.com:3306/billing?useSSL=true\n"
                + "  driverClassName: com.mysql.cj.jdbc.Driver\n"
                + "  username: readonly_user\n"
                + "  dialect: mysql\n"
                + "  maxRows: 1000\n"
                + "tables:\n"
                + "  - logicalName: contract_bill\n"
                + "    primaryKey: bill_id\n"
                + "    headers: [bill_id, receivable_amount]\n"
                + "    physicalName: billing.contract_bill\n");

        GenericLintIssue issue = findError(linter.lintConfig(tempDir.resolve("validator.yml")),
                "MISSING_JDBC_PASSWORD_ENV", "jdbc.passwordEnv");

        assertThat(issue.getSuggestion()).contains("passwordEnv: BIZ_READONLY_PASSWORD");
        assertThat(issue.getSuggestion()).contains("不要写 password");
    }

    @Test
    void suggestsReplacingPlaintextPasswordWithoutLeakingSecret() throws Exception {
        writeJdbcConfig(""
                + "schemaVersion: 1\n"
                + "type: jdbc\n"
                + "jdbc:\n"
                + "  url: jdbc:mysql://db.example.com:3306/billing?useSSL=true\n"
                + "  driverClassName: com.mysql.cj.jdbc.Driver\n"
                + "  username: readonly_user\n"
                + "  password: PlainText#123\n"
                + "  dialect: mysql\n"
                + "  maxRows: 1000\n"
                + "tables:\n"
                + "  - logicalName: contract_bill\n"
                + "    primaryKey: bill_id\n"
                + "    headers: [bill_id, receivable_amount]\n"
                + "    physicalName: billing.contract_bill\n");

        GenericLintIssue issue = findError(linter.lintConfig(tempDir.resolve("validator.yml")),
                "PLAINTEXT_JDBC_PASSWORD", "jdbc.password");

        assertThat(issue.getSuggestion()).contains("passwordEnv: BIZ_READONLY_PASSWORD");
        assertThat(issue.getSuggestion()).contains("删除 password");
        assertThat(issue.getSuggestion()).doesNotContain("PlainText#123");
    }

    @Test
    void suggestsRemovingSecretsFromJdbcUrlWithoutEchoingSensitiveConnectionString() throws Exception {
        writeJdbcConfig(""
                + "schemaVersion: 1\n"
                + "type: jdbc\n"
                + "jdbc:\n"
                + "  url: jdbc:mysql://db.example.com:3306/billing?user=readonly&password=Hidden#456\n"
                + "  driverClassName: com.mysql.cj.jdbc.Driver\n"
                + "  username: readonly_user\n"
                + "  passwordEnv: BIZ_READONLY_PASSWORD\n"
                + "  dialect: mysql\n"
                + "  maxRows: 1000\n"
                + "tables:\n"
                + "  - logicalName: contract_bill\n"
                + "    primaryKey: bill_id\n"
                + "    headers: [bill_id, receivable_amount]\n"
                + "    physicalName: billing.contract_bill\n");

        GenericLintIssue issue = findError(linter.lintConfig(tempDir.resolve("validator.yml")),
                "JDBC_URL_CONTAINS_SECRET", "jdbc.url");

        assertThat(issue.getSuggestion()).contains("jdbc:mysql://db.example.com:3306/billing?useSSL=true");
        assertThat(issue.getSuggestion()).contains("passwordEnv: BIZ_READONLY_PASSWORD");
        assertThat(issue.getSuggestion()).doesNotContain("Hidden#456");
        assertThat(issue.getSuggestion()).doesNotContain("password=Hidden#456");
    }

    @Test
    void suggestsHeadersWhitelistForJdbcTable() throws Exception {
        writeJdbcConfig(""
                + "schemaVersion: 1\n"
                + "type: jdbc\n"
                + "jdbc:\n"
                + "  url: jdbc:h2:mem:testdb\n"
                + "  driverClassName: org.h2.Driver\n"
                + "  username: sa\n"
                + "  dialect: h2\n"
                + "  maxRows: 1000\n"
                + "tables:\n"
                + "  - logicalName: contract_bill\n"
                + "    primaryKey: bill_id\n"
                + "    physicalName: billing.contract_bill\n");

        GenericLintIssue issue = findError(linter.lintConfig(tempDir.resolve("validator.yml")),
                "MISSING_JDBC_HEADERS", "tables[0].headers");

        assertThat(issue.getSuggestion()).contains("headers: [bill_id, receivable_amount]");
        assertThat(issue.getSuggestion()).contains("字段白名单");
    }

    @Test
    void suggestsReadonlySelectForUnsafeSql() throws Exception {
        writeJdbcConfig(""
                + "schemaVersion: 1\n"
                + "type: jdbc\n"
                + "jdbc:\n"
                + "  url: jdbc:h2:mem:testdb\n"
                + "  driverClassName: org.h2.Driver\n"
                + "  username: sa\n"
                + "  dialect: h2\n"
                + "  maxRows: 1000\n"
                + "tables:\n"
                + "  - logicalName: contract_bill\n"
                + "    primaryKey: bill_id\n"
                + "    headers: [bill_id, receivable_amount]\n"
                + "    sql: \"SELECT * FROM billing.contract_bill\"\n");

        GenericLintIssue issue = findError(linter.lintConfig(tempDir.resolve("validator.yml")),
                "UNSAFE_SQL", "tables[0].sql");

        assertThat(issue.getSuggestion()).contains("SELECT bill_id, receivable_amount");
        assertThat(issue.getSuggestion()).contains("FROM billing.contract_bill");
        assertThat(issue.getSuggestion()).contains("单条只读 SELECT");
    }

    @Test
    void suggestsKnownFieldsWhenRuleReferencesUnknownField() throws Exception {
        writeSource(""
                + "schemaVersion: 1\n"
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: contract_bill\n"
                + "    primaryKey: bill_id\n"
                + "    headers: [bill_id, receivable_amount]\n");
        writeRules(""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    category: SINGLE_FIELD_CONSTRAINT\n"
                + "    severity: CRITICAL\n"
                + "    templateCode: NOT_NULL\n"
                + "    templateParams:\n"
                + "      tableName: contract_bill\n"
                + "      fields: [missing_amount]\n");

        GenericLintIssue issue = findError(
                linter.lintRules(tempDir.resolve("rules.yml"), tempDir.resolve("source.yml")),
                "UNKNOWN_FIELD", "rules[0].templateParams.fields[0]");

        assertThat(issue.getSuggestion()).contains("fields: [receivable_amount]");
        assertThat(issue.getSuggestion()).contains("已声明字段");
        assertThat(issue.getSuggestion()).contains("bill_id");
    }

    private GenericLintIssue findError(GenericLintResult result, String code, String path) {
        Optional<GenericLintIssue> match = result.getErrors().stream()
                .filter(issue -> code.equals(issue.getCode()) && path.equals(issue.getPath()))
                .findFirst();
        assertThat(match).as(code + "@" + path).isPresent();
        return match.orElseThrow();
    }

    private void writeJdbcConfig(String sourceYaml) throws Exception {
        write("validator.yml", ""
                + "schemaVersion: 1\n"
                + "source:\n"
                + "  file: source.yml\n"
                + "rules:\n"
                + "  file: rules.yml\n");
        writeSource(sourceYaml);
        writeRules(""
                + "schemaVersion: 1\n"
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    category: SINGLE_FIELD_CONSTRAINT\n"
                + "    severity: CRITICAL\n"
                + "    templateCode: NOT_NULL\n"
                + "    templateParams:\n"
                + "      tableName: contract_bill\n"
                + "      fields: [receivable_amount]\n");
    }

    private void writeSource(String content) throws Exception {
        write("source.yml", content);
    }

    private void writeRules(String content) throws Exception {
        write("rules.yml", content);
    }

    private void write(String fileName, String content) throws Exception {
        Files.writeString(tempDir.resolve(fileName), content);
    }
}
