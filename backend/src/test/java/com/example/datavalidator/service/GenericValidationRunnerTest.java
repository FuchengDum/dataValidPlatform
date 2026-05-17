package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GenericValidationRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void runsGenericYamlRulePackageWithoutBuiltinRuleIds() throws Exception {
        write("source.yml", ""
                + "type: file\n"
                + "tables:\n"
                + "  - logicalName: contract_bill\n"
                + "    primaryKey: 账单ID\n"
                + "    headers: [账单ID, 合同金额, 减免金额, 应收金额]\n"
                + "    rows:\n"
                + "      - 账单ID: B001\n"
                + "        合同金额: 1000\n"
                + "        减免金额: 80\n"
                + "        应收金额: 920\n"
                + "      - 账单ID: B002\n"
                + "        合同金额: 1000\n"
                + "        减免金额: 80\n"
                + "        应收金额: 950\n");
        write("rules.yml", ""
                + "rules:\n"
                + "  - ruleId: C900\n"
                + "    ruleName: 应收金额关系校验\n"
                + "    category: SINGLE_BUSINESS_RULE\n"
                + "    severity: CRITICAL\n"
                + "    templateCode: ROW_EXPRESSION\n"
                + "    templateParams:\n"
                + "      tableName: contract_bill\n"
                + "      conditions:\n"
                + "        - left: { field: 应收金额 }\n"
                + "          operator: '=='\n"
                + "          right:\n"
                + "            op: '-'\n"
                + "            left: { field: 合同金额 }\n"
                + "            right: { field: 减免金额 }\n");
        write("validator.yml", ""
                + "source:\n"
                + "  type: file\n"
                + "  file: source.yml\n"
                + "rules:\n"
                + "  file: rules.yml\n"
                + "validation:\n"
                + "  failOnSeverity: CRITICAL\n"
                + "  formats: [json, markdown]\n"
                + "  outputDir: reports\n");

        GenericValidationRunner runner = runner();
        GenericValidationResult result = runner.run(tempDir.resolve("validator.yml"));

        assertThat(result.getTotalRules()).isEqualTo(1);
        assertThat(result.getExecutedRules()).isEqualTo(1);
        assertThat(result.getCriticalCount()).isEqualTo(1);
        assertThat(result.getFindings()).hasSize(1);
        assertThat(result.getFindings().get(0).getRecordKey()).isEqualTo("B002");
        assertThat(result.getReports()).containsKeys("json", "markdown");
        assertThat(Files.exists(Path.of(result.getReports().get("json")))).isTrue();
        assertThat(runner.exitCode(result, "CRITICAL")).isEqualTo(2);
    }

    @Test
    void rejectsDangerousSqlInput() {
        assertThatThrownBy(() -> SqlReadOnlyGuard.requireSelect("UPDATE t_order SET amount = 0"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SELECT");
        assertThatThrownBy(() -> SqlReadOnlyGuard.requireSelect("SELECT * FROM t_order; DELETE FROM t_order"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("注释或多语句");
    }

    private GenericValidationRunner runner() {
        ObjectMapper objectMapper = new ObjectMapper();
        GenericRuleAssetLoader loader = new GenericRuleAssetLoader(objectMapper);
        GenericDataSourceProvider dataSourceProvider = new GenericDataSourceProvider(loader, mock(JdbcTemplate.class));
        return new GenericValidationRunner(loader, dataSourceProvider,
                new GenericValidationReportWriter(objectMapper), new TemplateRuleExecutor());
    }

    private void write(String fileName, String content) throws Exception {
        Files.writeString(tempDir.resolve(fileName), content);
    }
}
