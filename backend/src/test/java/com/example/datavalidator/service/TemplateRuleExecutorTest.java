package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.RuleBinding;
import com.example.datavalidator.domain.RuleCategory;
import com.example.datavalidator.domain.RuleDefinition;
import com.example.datavalidator.domain.Severity;
import com.example.datavalidator.domain.ValidationFinding;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRuleExecutorTest {
    private final TemplateRuleExecutor executor = new TemplateRuleExecutor();

    @Test
    void notNullTemplateUsesConfiguredTableAndFields() {
        DataTable customerProfile = table("customer_profile", "客户编号",
                row("C001", "客户编号", "C001", "证件号", "110101199001010010", "手机号", ""),
                row("C002", "客户编号", "C002", "证件号", "110101199002020020", "手机号", "13800000000"));
        RuleDefinition rule = rule("C001", "客户资料必填字段");
        RuleBinding binding = binding("C001", "NOT_NULL", "customer_profile", "客户编号", "证件号", "手机号");

        List<ValidationFinding> findings = executor.execute(rule, tables(customerProfile), binding);

        assertThat(findings).hasSize(1);
        ValidationFinding finding = findings.get(0);
        assertThat(finding.getTableName()).isEqualTo("customer_profile");
        assertThat(finding.getRecordKey()).isEqualTo("C001");
        assertThat(finding.getFieldName()).isEqualTo("手机号");
        assertThat(finding.getExpectedValue()).isEqualTo("非空");
    }

    @Test
    void nonNegativeTemplateUsesConfiguredFieldsWithoutOrderTerms() {
        DataTable paymentRecord = table("payment_record", "支付流水号",
                row("P001", "支付流水号", "P001", "支付金额", "-12.50", "退款金额", "0"),
                row("P002", "支付流水号", "P002", "支付金额", "20", "退款金额", "1"));
        RuleDefinition rule = rule("P001", "支付金额非负");
        RuleBinding binding = binding("P001", "NON_NEGATIVE", "payment_record", "支付金额", "退款金额");

        List<ValidationFinding> findings = executor.execute(rule, tables(paymentRecord), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("P001");
        assertThat(findings.get(0).getFieldName()).isEqualTo("支付金额");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo(">= 0");
    }

    @Test
    void numericTypeTemplateReportsOnlyNonBlankInvalidNumbers() {
        DataTable paymentRecord = table("payment_record", "支付流水号",
                row("P001", "支付流水号", "P001", "支付金额", "not-a-number"),
                row("P002", "支付流水号", "P002", "支付金额", ""),
                row("P003", "支付流水号", "P003", "支付金额", "20.05"));
        RuleDefinition rule = rule("P002", "支付金额数值类型");
        RuleBinding binding = binding("P002", "NUMERIC_TYPE", "payment_record", "支付金额");

        List<ValidationFinding> findings = executor.execute(rule, tables(paymentRecord), binding);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRecordKey()).isEqualTo("P001");
        assertThat(findings.get(0).getExpectedValue()).isEqualTo("数值类型");
    }

    private RuleDefinition rule(String ruleId, String ruleName) {
        RuleDefinition rule = new RuleDefinition();
        rule.setRuleId(ruleId);
        rule.setRuleName(ruleName);
        rule.setCategory(RuleCategory.SINGLE_FIELD_CONSTRAINT);
        rule.setSeverity(Severity.CRITICAL);
        return rule;
    }

    private RuleBinding binding(String ruleId, String templateCode, String tableName, String... fields) {
        RuleBinding binding = new RuleBinding();
        binding.setRuleId(ruleId);
        binding.setExecutorType("TEMPLATE");
        binding.setTemplateCode(templateCode);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tableName", tableName);
        params.put("fields", Arrays.asList(fields));
        binding.setTemplateParams(params);
        return binding;
    }

    private Map<String, DataTable> tables(DataTable table) {
        Map<String, DataTable> tables = new LinkedHashMap<>();
        tables.put(table.getLogicalName(), table);
        return tables;
    }

    private DataTable table(String logicalName, String primaryKeyField, DataRow... rows) {
        DataTable table = new DataTable();
        table.setLogicalName(logicalName);
        table.setHeaders(Arrays.asList(primaryKeyField));
        table.setRows(Arrays.asList(rows));
        return table;
    }

    private DataRow row(String primaryKey, String... values) {
        DataRow row = new DataRow();
        row.setPrimaryKey(primaryKey);
        Map<String, String> cells = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            cells.put(values[i], values[i + 1]);
        }
        row.setValues(cells);
        return row;
    }
}
